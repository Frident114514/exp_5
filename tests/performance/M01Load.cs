using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Sockets;
using System.IO;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace M01Load {
public static class Runner {
    public static CancellationTokenSource Stop = new CancellationTokenSource();
    public static void Cancel() { Stop.Cancel(); }
    public static string Phase = "idle";
    public static double PhaseElapsedMs;
    public sealed class User {
        public string account { get; set; }
        public string password { get; set; }
        public string accessToken { get; set; }
    }
    public sealed class Sample {
        public int id { get; set; }
        public int status { get; set; }
        public bool valid { get; set; }
        public double writeStartMs { get; set; } = -1;
        public double writeCompletedMs { get; set; } = -1;
        public double firstByteMs { get; set; } = -1;
        public double headersReceivedMs { get; set; } = -1;
        public double responseCompletedMs { get; set; } = -1;
        public double parseCompletedMs { get; set; } = -1;
        public string failure { get; set; } = "none";
        public string exceptionType { get; set; }
        public string socketError { get; set; }
    }
    static readonly JsonSerializerOptions JsonOptions = new JsonSerializerOptions { WriteIndented=true };
    static double P(IEnumerable<double> values, double q) {
        var a=values.OrderBy(x=>x).ToArray();
        return a.Length==0?-1:a[(int)Math.Ceiling(q*a.Length)-1];
    }
    static int Peak(ref int peak, int value) {
        int observed;
        do { observed=peak; if(value<=observed)break; }
        while(Interlocked.CompareExchange(ref peak,value,observed)!=observed);
        return value;
    }
    static string Failure(Exception error) {
        if(error is OperationCanceledException) return Stop.IsCancellationRequested?"client_cancelled":"timeout";
        if(error is JsonException || error is FormatException || error is InvalidDataException) return "protocol_or_json";
        if(error is SocketException || error.InnerException is SocketException) return "socket";
        if(error is IOException) return "io";
        return "other";
    }
    // Read the HTTP message boundary, not EOF; firstByteMs is the first observed read byte.
    sealed class Reader {
        readonly NetworkStream stream;
        readonly CancellationToken token;
        readonly Sample sample;
        readonly Stopwatch clock;
        readonly byte[] buffer=new byte[8192];
        int pos,count;
        public Reader(NetworkStream stream,CancellationToken token,Sample sample,Stopwatch clock) {
            this.stream=stream;this.token=token;this.sample=sample;this.clock=clock;
        }
        async Task Fill() {
            count=await stream.ReadAsync(buffer,token).ConfigureAwait(false);pos=0;
            if(count==0)throw new EndOfStreamException("HTTP message ended prematurely");
            if(sample.firstByteMs<0)sample.firstByteMs=clock.Elapsed.TotalMilliseconds;
        }
        public async Task<string> Line() {
            using var bytes=new MemoryStream();
            bool cr=false;
            while(true) {
                if(pos==count)await Fill().ConfigureAwait(false);
                byte b=buffer[pos++];
                if(cr) { if(b!=10)throw new InvalidDataException("Invalid HTTP line");break; }
                if(b==13){cr=true;continue;}
                bytes.WriteByte(b);
                if(bytes.Length>16384)throw new InvalidDataException("HTTP line too long");
            }
            return Encoding.ASCII.GetString(bytes.ToArray());
        }
        public async Task Copy(int length,MemoryStream output) {
            if(length<0 || output.Length+length>1048576)throw new InvalidDataException("HTTP body too large");
            while(length>0) {
                if(pos==count)await Fill().ConfigureAwait(false);
                int n=Math.Min(length,count-pos);output.Write(buffer,pos,n);pos+=n;length-=n;
            }
        }
        public async Task<string> Response() {
            string status=await Line().ConfigureAwait(false);
            sample.status=int.Parse(status.Split(' ')[1]);
            int length=-1,headerBytes=0;bool chunked=false;
            while(true) {
                var line=await Line().ConfigureAwait(false);if(line.Length==0)break;
                headerBytes+=line.Length;if(headerBytes>65536)throw new InvalidDataException("Headers too large");
                int colon=line.IndexOf(':');if(colon<0)throw new InvalidDataException("Invalid HTTP header");
                var key=line.Substring(0,colon);var value=line.Substring(colon+1).Trim();
                if(key.Equals("Content-Length",StringComparison.OrdinalIgnoreCase))length=int.Parse(value);
                if(key.Equals("Transfer-Encoding",StringComparison.OrdinalIgnoreCase))chunked=value.Equals("chunked",StringComparison.OrdinalIgnoreCase);
            }
            sample.headersReceivedMs=clock.Elapsed.TotalMilliseconds;
            using var body=new MemoryStream();
            if(chunked) {
                while(true) {
                    var line=await Line().ConfigureAwait(false);
                    int n=Convert.ToInt32(line.Split(';')[0],16);
                    if(n==0){while((await Line().ConfigureAwait(false)).Length!=0){}break;}
                    await Copy(n,body).ConfigureAwait(false);
                    if((await Line().ConfigureAwait(false)).Length!=0)throw new InvalidDataException("Invalid chunk delimiter");
                }
            } else if(length>=0)await Copy(length,body).ConfigureAwait(false);
            else throw new InvalidDataException("Response must use Content-Length or chunked framing");
            sample.responseCompletedMs=clock.Elapsed.TotalMilliseconds;
            return Encoding.UTF8.GetString(body.ToArray());
        }
    }
    public static async Task<string> Run(string file,string api,string mode,int rate,int seconds,int burst,
            bool preconnect=false,string trial="trial",string detailsFile=null) {
        if(preconnect && mode!="me")throw new ArgumentException("Preconnected mode supports me only");
        var users=JsonSerializer.Deserialize<User[]>(File.ReadAllText(file));
        var records=new ConcurrentBag<Sample>();
        using var handler=new SocketsHttpHandler {MaxConnectionsPerServer=20000,ConnectTimeout=TimeSpan.FromSeconds(3),UseProxy=false};
        using var client=new HttpClient(handler){Timeout=Timeout.InfiniteTimeSpan};
        var sockets=new TcpClient[preconnect?burst:0];
        int connectionsEstablished=0;
        var elapsed=new Stopwatch();
        int active=0,maxActive=0,awaiting=0,maxAwaiting=0,dropped=0;
        Phase="preconnect";PhaseElapsedMs=0;
        try {
            if(preconnect) {
                var uri=new Uri(api);
                await Parallel.ForEachAsync(Enumerable.Range(0,burst),
                    new ParallelOptions{MaxDegreeOfParallelism=128,CancellationToken=Stop.Token},async(i,ct)=>{
                        for(int attempt=0;;attempt++) {
                            var socket=new TcpClient{NoDelay=true};sockets[i]=socket;
                            try {await socket.ConnectAsync(uri.Host,uri.Port,ct).ConfigureAwait(false);Interlocked.Increment(ref connectionsEstablished);break;}
                            catch(SocketException)when(attempt<4){socket.Dispose();await Task.Delay(100,ct).ConfigureAwait(false);}
                        }
                    }).ConfigureAwait(false);
            }
            elapsed.Start();Phase="requests";
            async Task Send(int index) {
                Peak(ref maxActive,Interlocked.Increment(ref active));
                var user=users[index%users.Length];var s=new Sample{id=index};bool pending=false;
                try {
                    string body;
                    if(preconnect) {
                        using var timeout=CancellationTokenSource.CreateLinkedTokenSource(Stop.Token);
                        timeout.CancelAfter(TimeSpan.FromSeconds(15));
                        var uri=new Uri(api+"/users/me");
                        var bytes=Encoding.ASCII.GetBytes($"GET {uri.PathAndQuery} HTTP/1.1\r\nHost: {uri.Authority}\r\nAuthorization: Bearer {user.accessToken}\r\nX-M01-Trial: {trial}\r\nX-M01-Request-Id: {index}\r\nConnection: close\r\n\r\n");
                        var stream=sockets[index].GetStream();
                        s.writeStartMs=elapsed.Elapsed.TotalMilliseconds;
                        await stream.WriteAsync(bytes,timeout.Token).ConfigureAwait(false);
                        s.writeCompletedMs=elapsed.Elapsed.TotalMilliseconds;
                        pending=true;Peak(ref maxAwaiting,Interlocked.Increment(ref awaiting));
                        body=await new Reader(stream,timeout.Token,s,elapsed).Response().ConfigureAwait(false);
                        Interlocked.Decrement(ref awaiting);pending=false;
                    } else {
                        using var requestTimeout=CancellationTokenSource.CreateLinkedTokenSource(Stop.Token);
                        requestTimeout.CancelAfter(TimeSpan.FromSeconds(15));
                        using var request=new HttpRequestMessage(mode=="login"?HttpMethod.Post:HttpMethod.Get,api+(mode=="login"?"/auth/login":"/users/me"));
                        request.Headers.Add("X-M01-Trial",trial);request.Headers.Add("X-M01-Request-Id",index.ToString());
                        if(mode=="login")request.Content=new StringContent(JsonSerializer.Serialize(new{account=user.account,password=user.password}),Encoding.UTF8,"application/json");
                        else request.Headers.Authorization=new AuthenticationHeaderValue("Bearer",user.accessToken);
                        s.writeStartMs=elapsed.Elapsed.TotalMilliseconds;
                        using var response=await client.SendAsync(request,HttpCompletionOption.ResponseHeadersRead,requestTimeout.Token).ConfigureAwait(false);
                        s.headersReceivedMs=elapsed.Elapsed.TotalMilliseconds;s.status=(int)response.StatusCode;
                        body=await response.Content.ReadAsStringAsync(requestTimeout.Token).ConfigureAwait(false);
                        s.responseCompletedMs=elapsed.Elapsed.TotalMilliseconds;
                        // HttpClient does not expose socket write completion or first-byte time.
                    }
                    if(s.status==200) {
                        using var json=JsonDocument.Parse(body);var data=json.RootElement.GetProperty("data");
                        s.valid=json.RootElement.GetProperty("code").GetString()=="OK" &&
                            (mode=="login"?data.GetProperty("user"):data).GetProperty("account").GetString()==user.account;
                    }
                } catch(Exception error) {
                    s.failure=Failure(error);s.exceptionType=error.GetType().Name;
                    var socket=error as SocketException ?? error.InnerException as SocketException;
                    s.socketError=socket?.SocketErrorCode.ToString();
                } finally {
                    if(pending)Interlocked.Decrement(ref awaiting);
                    s.parseCompletedMs=elapsed.Elapsed.TotalMilliseconds;
                    records.Add(s);Interlocked.Decrement(ref active);PhaseElapsedMs=elapsed.Elapsed.TotalMilliseconds;
                }
            }
            var tasks=new List<Task>();
            if(burst>0) {
                var gate=new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
                async Task Gated(int index){await gate.Task.ConfigureAwait(false);await Send(index).ConfigureAwait(false);}
                for(int i=0;i<burst;i++)tasks.Add(Gated(i));elapsed.Restart();gate.SetResult(true);
            } else {
                for(int i=0;i<rate*seconds&&!Stop.IsCancellationRequested;i++) {
                    double remaining=i*1000.0/rate-elapsed.Elapsed.TotalMilliseconds;
                    if(remaining>0)await Task.Delay(TimeSpan.FromMilliseconds(remaining),Stop.Token).ConfigureAwait(false);
                    if(Volatile.Read(ref active)>=1000){dropped++;continue;}tasks.Add(Send(i));
                }
            }
            await Task.WhenAll(tasks).ConfigureAwait(false);
        } finally {foreach(var socket in sockets)socket?.Dispose();Phase="complete";}
        var all=records.OrderBy(s=>s.id).ToArray();
        var good=all.Where(s=>s.status==200&&s.valid&&s.failure=="none").ToArray();
        double Latency(Sample s)=>(s.responseCompletedMs>=0?s.responseCompletedMs:s.parseCompletedMs)-s.writeStartMs;
        double ParseLatency(Sample s)=>s.parseCompletedMs-s.writeStartMs;
        double successRate=all.Length==0?0:(double)good.Length/all.Length;
        double Spread(IEnumerable<double> values){var a=values.Where(x=>x>=0).ToArray();return a.Length==0?-1:a.Max()-a.Min();}
        double First(IEnumerable<double> values){var a=values.Where(x=>x>=0).ToArray();return a.Length==0?-1:a.Min();}
        // Reconstruct half-open [write complete, response complete) intervals, excluding JSON parsing.
        var events=all.Where(s=>s.writeCompletedMs>=0&&s.responseCompletedMs>=0)
            .SelectMany(s=>new[]{(time:s.writeCompletedMs,delta:1),(time:s.responseCompletedMs,delta:-1)})
            .OrderBy(e=>e.time).ThenBy(e=>e.delta);
        int overlap=0,peakOverlap=0;foreach(var e in events){overlap+=e.delta;peakOverlap=Math.Max(peakOverlap,overlap);}
        var statusTimes=all.GroupBy(s=>s.status).ToDictionary(g=>g.Key.ToString(),g=>new{count=g.Count(),p95Ms=P(g.Select(Latency),.95),p99Ms=P(g.Select(Latency),.99)});
        if(detailsFile!=null)File.WriteAllText(detailsFile,JsonSerializer.Serialize(all));
        double duration=elapsed.Elapsed.TotalSeconds;
        return JsonSerializer.Serialize(new{
            engine=preconnect?"dotnet-preconnected-http11-v2":"dotnet-httpclient-v2",trial,mode,scheduled=burst>0?burst:rate*seconds,
            requests=all.Length,success=good.Length,successRate,rejected429=all.Count(s=>s.status==429),rejected503=all.Count(s=>s.status==503),
            transportErrors=all.Count(s=>s.status==0),failures=all.Where(s=>s.failure!="none").GroupBy(s=>s.failure).ToDictionary(g=>g.Key,g=>g.Count()),
            invalid200=all.Count(s=>s.status==200&&!s.valid),dropped,connectionsEstablished,fullyWritten=all.Count(s=>s.writeCompletedMs>=0),
            maxActiveTasks=maxActive,maxWrittenAwaitingObservedComplete=maxAwaiting,clientWrittenResponseIntervalPeak=peakOverlap,
            intervalMeaning="client observed socket write completion to HTTP message read completion; not server execution or wire timestamps",
            writeStartSpreadMs=Spread(all.Select(s=>s.writeStartMs)),writeCompletedSpreadMs=Spread(all.Select(s=>s.writeCompletedMs)),
            firstObservedResponseByteMs=First(all.Select(s=>s.firstByteMs)),firstCompleteResponseMs=First(all.Select(s=>s.responseCompletedMs)),
            elapsedSeconds=duration,successfulRps=good.Length/duration,
            successP95Ms=P(good.Select(Latency),.95),successP99Ms=P(good.Select(Latency),.99),
            successIncludingParseP95Ms=P(good.Select(ParseLatency),.95),latencyByStatus=statusTimes,
            pass=successRate>.99&&P(good.Select(Latency),.95)>=0&&P(good.Select(Latency),.95)<1000&&dropped==0
        },JsonOptions);
    }
    public static async Task<string> Calibrate(string file,int count,string detailsFile=null) {
        var users=JsonSerializer.Deserialize<User[]>(File.ReadAllText(file));
        var listener=new TcpListener(IPAddress.Loopback,0);listener.Start(1024);
        using var timeout=new CancellationTokenSource(TimeSpan.FromSeconds(60));
        int port=((IPEndPoint)listener.LocalEndpoint).Port;
        var gate=new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var ids=new ConcurrentDictionary<int,bool>();int arrived=0,duplicate=0,active=0,peak=0,responded=0;
        var received=new ConcurrentBag<(int id,long enter,long exit)>();
        var clock=Stopwatch.StartNew();
        async Task Receive(TcpClient client) {
            using(client) {
                var sample=new Sample();var stream=client.GetStream();var reader=new Reader(stream,timeout.Token,sample,clock);
                if(!(await reader.Line().ConfigureAwait(false)).StartsWith("GET "))throw new InvalidDataException("Not GET");
                int id=-1;int headers=0;
                while(true){var line=await reader.Line().ConfigureAwait(false);if(line.Length==0)break;
                    headers+=line.Length;if(headers>16384)throw new InvalidDataException("Headers too large");
                    if(line.StartsWith("X-M01-Request-Id:",StringComparison.OrdinalIgnoreCase))id=int.Parse(line.Split(':')[1]);}
                if(id<0||id>=count)throw new InvalidDataException("Unexpected request id");
                if(!ids.TryAdd(id,true))Interlocked.Increment(ref duplicate);
                long enter=clock.ElapsedTicks;Peak(ref peak,Interlocked.Increment(ref active));
                if(Interlocked.Increment(ref arrived)==count)gate.TrySetResult(true);
                await gate.Task.WaitAsync(timeout.Token).ConfigureAwait(false);
                var body=Encoding.UTF8.GetBytes(JsonSerializer.Serialize(new{code="OK",data=new{account=users[id%users.Length].account}}));
                var header=Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n");
                await stream.WriteAsync(header,timeout.Token).ConfigureAwait(false);await stream.WriteAsync(body,timeout.Token).ConfigureAwait(false);
                received.Add((id,enter,clock.ElapsedTicks));Interlocked.Decrement(ref active);Interlocked.Increment(ref responded);
            }
        }
        async Task Serve() {
            var tasks=new List<Task>();
            for(int i=0;i<count;i++)tasks.Add(Receive(await listener.AcceptTcpClientAsync(timeout.Token).ConfigureAwait(false)));
            await Task.WhenAll(tasks).ConfigureAwait(false);
        }
        var server=Serve();
        try {
            string clientResult=await Run(file,$"http://127.0.0.1:{port}/api/v1","me",0,0,count,true,"calibration",detailsFile).ConfigureAwait(false);
            await server.ConfigureAwait(false);
            return JsonSerializer.Serialize(new{purpose="load generator calibration only; responses intentionally held, not business capacity",
                received=arrived,uniqueIds=ids.Count,duplicates=duplicate,responded,peakCompleteHeadersAwaitingRelease=peak,
                allArrivedBeforeResponses=arrived==count&&peak==count&&duplicate==0,client=JsonDocument.Parse(clientResult).RootElement},JsonOptions);
        } finally {timeout.Cancel();listener.Stop();try{await server.ConfigureAwait(false);}catch{} }
    }
}
}
