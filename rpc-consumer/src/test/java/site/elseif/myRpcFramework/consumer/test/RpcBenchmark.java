package site.elseif.myRpcFramework.consumer.test;
import lombok.extern.slf4j.Slf4j;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import site.elseif.myRpcFramework.api.BenchmarkPOJO;
import site.elseif.myRpcFramework.api.BenchmarkService;
import site.elseif.myRpcFramework.api.HelloService;
import site.elseif.myRpcFramework.common.exception.CircuitBreakerException;
import site.elseif.myRpcFramework.common.exception.FlowLimitException;
import site.elseif.myRpcFramework.common.exception.RpcException;
import site.elseif.myRpcFramework.core.discovery.NacosListenerServiceDiscovery;
import site.elseif.myRpcFramework.core.proxy.ClientProxy;
import site.elseif.myRpcFramework.common.MessageConstants;

import java.util.List;
import java.util.concurrent.TimeUnit;


// 基准测试配置
@BenchmarkMode({Mode.Throughput, Mode.SampleTime}) // 吞吐量模式
@OutputTimeUnit(TimeUnit.MILLISECONDS) // 结果单位：毫秒
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS) // 预热 3 轮，每轮 1 秒
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS) // 正式测试 5 轮，每轮 3 秒
@Fork(1) // 进程数
@State(Scope.Thread) // 每个线程独立享有一个实例
@Slf4j
public class RpcBenchmark {

    /**
     * 使用@Param注解，可以方便地在命令行或IDE中指定不同的线程数进行测试。
     * 例如，可以分别测试 1, 2, 4, 8, 16 个线程。
     */
    @Param({"1", "2", "4", "8"})
    public int threadCount;

    private ClientProxy proxy;
    private HelloService helloService;
    private BenchmarkService benchmarkService;

    @Setup(Level.Trial) // 初始化阶段：在测试开始前执行一次
    public void init() throws Exception {
        // 初始化连接
        NacosListenerServiceDiscovery serviceDiscovery = new NacosListenerServiceDiscovery("127.0.0.1:8848", "public");
        proxy = new ClientProxy(MessageConstants.SERIALIZER_KRYO, serviceDiscovery);
//        proxy.enableNacosConfig("127.0.0.1:8848", "service-rules.yaml", "DEFAULT_GROUP");
//        helloService = proxy.getProxy(HelloService.class);
        benchmarkService = proxy.getProxy(BenchmarkService.class);
    }

    @TearDown(Level.Trial) // 销毁阶段：测试结束后执行
    public void shutdown() {
        if (proxy != null) {
            proxy.close();
        }
    }

    /**
     * 测试 echo 方法（无参返回）
     * 对应你的 sayHello 测试
     */
    @Benchmark
    public void testEcho(Blackhole bh) {
        try {
            String result = benchmarkService.echo("Benchmark");
            bh.consume(result);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * 测试 getPOJO 方法（pojo返回值）
     * 对应你的 pojo 返回值场景
     */
    @Benchmark
    public void testGetPOJO(Blackhole bh) {
        try {
            BenchmarkPOJO result = benchmarkService.getPOJO();
            bh.consume(result);
        } catch (Exception e) {
            bh.consume(e);
        }
    }

    /**
     * 测试 getPOJOList 方法（列表返回值）
     */
    @Benchmark
    public void testGetPOJOList(Blackhole bh) {
        try {
            List<BenchmarkPOJO> result = benchmarkService.getPOJOList();
            bh.consume(result);
        } catch (Exception e) {
            bh.consume(e);
        }
    }
}