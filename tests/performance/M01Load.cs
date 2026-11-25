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
    sealed class User {
        public string account { get; set; }
        public string password { get; set; }
        public string accessToken { get; set; }
    }
    sealed class Sample {
        public int status; public double ms; public bool valid; public double dispatchMs;
    }
    public static async Task<string> Run(string file, string api, string mode, int rate, int seconds, int burst, bool preconnect = false) {
        var users = JsonSerializer.Deserialize<User[]>(System.IO.File.ReadAllText(file));
        var records = new ConcurrentBag<Sample>();
        var handler = new SocketsHttpHandler { MaxConnectionsPerServer = 20000, ConnectTimeout = TimeSpan.FromSeconds(3),
            UseProxy = false, PooledConnectionLifetime = TimeSpan.FromMinutes(5) };
        using var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(10) };
        int active = 0, maximum = 0, dropped = 0;
        int sent = 0, awaitingResponse = 0, maxAwaitingResponse = 0;
        double firstResponseMs = -1, lastDispatchMs = -1;
        var sockets = new TcpClient[preconnect ? burst : 0];
        if (preconnect) {
            // Establish real connections first; do not hold or delay server responses.
            // A simultaneous gate is a start schedule, not proof of 10000 server executions.
            var uri = new Uri(api);
            try {
                await Parallel.ForEachAsync(Enumerable.Range(0, burst),
                    new ParallelOptions { MaxDegreeOfParallelism = 128, CancellationToken = Stop.Token }, async (i, ct) => {
                        for (int attempt = 0; ; attempt++) {
                            var socket = new TcpClient { NoDelay = true };
                            sockets[i] = socket;
                            try {
                                await socket.ConnectAsync(uri.Host, uri.Port, ct).ConfigureAwait(false);
                                break;
                            } catch (SocketException) when (attempt < 4) {
                                socket.Dispose();
                                await Task.Delay(100, ct).ConfigureAwait(false);
                            }
                        }
                    }).ConfigureAwait(false);
            } catch (Exception failure) {
                int connected = sockets.Count(s => s != null && s.Connected);
                foreach (var socket in sockets) socket?.Dispose();
                throw new IOException($"Preconnection failed after {connected}/{burst} established connections", failure);
            }
        }
        var elapsed = Stopwatch.StartNew();
        async Task Send(int index) {
            int current = Interlocked.Increment(ref active);
            int observed;
            do { observed = maximum; if (current <= observed) break; }
            while (Interlocked.CompareExchange(ref maximum, current, observed) != observed);
            var user = users[index % users.Length];
            var start = Stopwatch.StartNew();
            var sample = new Sample { dispatchMs = elapsed.Elapsed.TotalMilliseconds };
            try {
                if (preconnect) {
                    using var socket = sockets[index];
                    using var timeout = CancellationTokenSource.CreateLinkedTokenSource(Stop.Token);
                    timeout.CancelAfter(TimeSpan.FromSeconds(10));
                    var uri = new Uri(api + "/users/me");
                    var bytes = Encoding.ASCII.GetBytes($"GET {uri.PathAndQuery} HTTP/1.1\r\nHost: {uri.Authority}\r\nAuthorization: Bearer {user.accessToken}\r\nConnection: close\r\n\r\n");
                    var stream = socket.GetStream();
                    int pending = Interlocked.Increment(ref awaitingResponse);
                    int previous;
                    do { previous = maxAwaitingResponse; if (pending <= previous) break; }
                    while (Interlocked.CompareExchange(ref maxAwaitingResponse, pending, previous) != previous);
                    try {
                        await stream.WriteAsync(bytes, timeout.Token).ConfigureAwait(false);
                        sample.dispatchMs = elapsed.Elapsed.TotalMilliseconds;
                        Interlocked.Increment(ref sent);
                        using var responseBytes = new MemoryStream();
                        await stream.CopyToAsync(responseBytes, timeout.Token).ConfigureAwait(false);
                        double received = elapsed.Elapsed.TotalMilliseconds;
                        Interlocked.CompareExchange(ref firstResponseMs, received, -1);
                        var raw = Encoding.UTF8.GetString(responseBytes.ToArray());
                        int headerEnd = raw.IndexOf("\r\n\r\n", StringComparison.Ordinal);
                        if (headerEnd < 0) throw new IOException("Incomplete HTTP response");
                        sample.status = int.Parse(raw.Split(' ')[1]);
                        string rawBody = raw.Substring(headerEnd + 4);
                        if (raw.Substring(0, headerEnd).Contains("Transfer-Encoding: chunked", StringComparison.OrdinalIgnoreCase)) {
                            // Decode byte lengths before UTF-8 decoding (body may contain Chinese).
                            var full = responseBytes.ToArray();
                            int offset = Encoding.UTF8.GetByteCount(raw.Substring(0, headerEnd + 4));
                            using var decoded = new MemoryStream();
                            while (true) {
                                int end = Array.IndexOf(full, (byte)'\r', offset);
                                int length = Convert.ToInt32(Encoding.ASCII.GetString(full, offset, end-offset).Split(';')[0], 16);
                                if (length == 0) break;
                                offset = end + 2;
                                decoded.Write(full, offset, length);
                                offset += length + 2;
                            }
                            rawBody = Encoding.UTF8.GetString(decoded.ToArray());
                        }
                        if (sample.status == 200) {
                            using var json = JsonDocument.Parse(rawBody);
                            sample.valid = json.RootElement.GetProperty("code").GetString() == "OK"
                                && json.RootElement.GetProperty("data").GetProperty("account").GetString() == user.account;
                        }
                    } finally { Interlocked.Decrement(ref awaitingResponse); }
                    sample.ms = start.Elapsed.TotalMilliseconds;
                    return;
                }
                using var req = new HttpRequestMessage(mode == "login" ? HttpMethod.Post : HttpMethod.Get,
                    api + (mode == "login" ? "/auth/login" : "/users/me"));
                if (mode == "login") req.Content = new StringContent(JsonSerializer.Serialize(new { account=user.account, password=user.password }), Encoding.UTF8, "application/json");
                else req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", user.accessToken);
                using var response = await client.SendAsync(req, Stop.Token).ConfigureAwait(false);
                sample.status = (int)response.StatusCode;
                string body = await response.Content.ReadAsStringAsync(Stop.Token).ConfigureAwait(false);
                sample.ms = start.Elapsed.TotalMilliseconds;
                if (sample.status == 200) {
                    using var json = JsonDocument.Parse(body);
                    var data = json.RootElement.GetProperty("data");
                    sample.valid = json.RootElement.GetProperty("code").GetString() == "OK"
                        && (mode == "login" ? data.GetProperty("user") : data).GetProperty("account").GetString() == user.account;
                }
            } catch (Exception) {
                if (sample.status == 0) sample.status = 0;
                sample.ms = start.Elapsed.TotalMilliseconds;
            } finally {
                records.Add(sample); Interlocked.Decrement(ref active);
            }
        }
        var tasks = new List<Task>();
        if (burst > 0) {
            var gate = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
            async Task Gated(int index) { await gate.Task.ConfigureAwait(false); await Send(index).ConfigureAwait(false); }
            for (int i=0; i<burst; i++) tasks.Add(Gated(i));
            elapsed.Restart();
            gate.SetResult(true);
        } else {
            int total = rate * seconds;
            for (int i=0; i<total && !Stop.IsCancellationRequested; i++) {
                double due = i * 1000.0 / rate;
                double remaining = due - elapsed.Elapsed.TotalMilliseconds;
                if (remaining > 0) await Task.Delay(TimeSpan.FromMilliseconds(remaining), Stop.Token).ConfigureAwait(false);
                if (Volatile.Read(ref active) >= 1000) { dropped++; continue; }
                tasks.Add(Send(i));
            }
        }
        await Task.WhenAll(tasks).ConfigureAwait(false);
        var samples = records.ToArray();
        if (preconnect) lastDispatchMs = samples.Max(s => s.dispatchMs);
        var good = samples.Where(s => s.status == 200 && s.valid).ToArray();
        double Percentile(IEnumerable<double> items, double q) {
            var sorted=items.OrderBy(x=>x).ToArray();
            return sorted.Length == 0 ? -1 : sorted[(int)Math.Ceiling(q*sorted.Length)-1];
        }
        var allMs=samples.Select(s=>s.ms);
        double duration=elapsed.Elapsed.TotalSeconds;
        double successP95=Percentile(good.Select(s=>s.ms),.95);
        double successRate=samples.Length==0 ? 0 : (double)good.Length/samples.Length;
        return JsonSerializer.Serialize(new {
            engine=preconnect?"dotnet-preconnected-http11":"dotnet-async-http", mode, offeredRate=rate, scheduled=burst>0?burst:rate*seconds,
            preconnected=preconnect?sockets.Length:0, fullyWritten=sent, maxAwaitingResponse,
            firstResponseMs, lastDispatchMs,
            requests=samples.Length, success=good.Length, successRate, rejected429=samples.Count(s=>s.status==429),
            rejected503=samples.Count(s=>s.status==503), transportErrors=samples.Count(s=>s.status==0),
            otherResponses=samples.Count(s=>s.status!=0 && s.status!=200 && s.status!=429 && s.status!=503),
            invalid200=samples.Count(s=>s.status==200 && !s.valid), dropped, maxInFlight=maximum,
            elapsedSeconds=duration, completedRps=samples.Length/duration, successfulRps=good.Length/duration,
            p95Ms=Percentile(allMs,.95), p99Ms=Percentile(allMs,.99), maxMs=samples.Length==0?-1:samples.Max(s=>s.ms),
            successP95Ms=successP95, successP99Ms=Percentile(good.Select(s=>s.ms),.99),
            dispatchSpreadMs=samples.Length==0?0:samples.Max(s=>s.dispatchMs)-samples.Min(s=>s.dispatchMs),
            pass=successRate>.99 && successP95>=0 && successP95<1000 && dropped==0,
            statuses=samples.GroupBy(s=>s.status).ToDictionary(g=>g.Key.ToString(),g=>g.Count())
        },new JsonSerializerOptions { WriteIndented=true });
    }
}
}
