using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
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
    public static async Task<string> Run(string file, string api, string mode, int rate, int seconds, int burst) {
        var users = JsonSerializer.Deserialize<User[]>(System.IO.File.ReadAllText(file));
        var records = new ConcurrentBag<Sample>();
        var handler = new SocketsHttpHandler { MaxConnectionsPerServer = 20000, ConnectTimeout = TimeSpan.FromSeconds(3),
            UseProxy = false, PooledConnectionLifetime = TimeSpan.FromMinutes(5) };
        using var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(10) };
        int active = 0, maximum = 0, dropped = 0;
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
            engine="dotnet-async-http", mode, offeredRate=rate, scheduled=burst>0?burst:rate*seconds,
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
