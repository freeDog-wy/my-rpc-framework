package site.elseif.myRpcFramework.api;

import lombok.Data;

import java.io.Serializable;

@Data
public class BenchmarkPOJO implements Serializable {
    private String field1;
    private String field2;
    private int field3;
    private long field4;
}
