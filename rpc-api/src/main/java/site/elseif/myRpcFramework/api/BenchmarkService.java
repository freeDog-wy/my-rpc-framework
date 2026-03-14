package site.elseif.myRpcFramework.api;

public interface BenchmarkService {
    String echo(String message);                    // 无参返回
    BenchmarkPOJO getPOJO();                         // pojo返回值
    java.util.List<BenchmarkPOJO> getPOJOList();     // pojo列表返回值
}
