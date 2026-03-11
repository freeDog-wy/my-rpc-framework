package site.elseif.myRpcFramework.provider;


import site.elseif.myRpcFramework.api.BenchmarkPOJO;
import site.elseif.myRpcFramework.api.BenchmarkService;

import java.util.ArrayList;
import java.util.List;

/**
 * Dubbo 服务实现
 */
public class BenchmarkServiceImpl implements BenchmarkService {

    @Override
    public String echo(String message) {
        return "Hello: " + message;
    }

    @Override
    public BenchmarkPOJO getPOJO() {
        BenchmarkPOJO pojo = new BenchmarkPOJO();
        pojo.setField1("value1");
        pojo.setField2("value2");
        pojo.setField3(123);
        pojo.setField4(456L);
        return pojo;
    }

    @Override
    public List<BenchmarkPOJO> getPOJOList() {
        List<BenchmarkPOJO> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            BenchmarkPOJO pojo = new BenchmarkPOJO();
            pojo.setField1("value" + i);
            pojo.setField2("value" + i);
            pojo.setField3(i);
            pojo.setField4(i * 100L);
            list.add(pojo);
        }
        return list;
    }
}


