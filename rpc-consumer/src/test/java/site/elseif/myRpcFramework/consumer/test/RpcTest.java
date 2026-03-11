package site.elseif.myRpcFramework.consumer.test;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class RpcTest {
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(RpcBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
