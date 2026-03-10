package site.elseif.myRpcFramework.provider;

import site.elseif.myRpcFramework.api.HelloService;

public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }

    @Override
    public String sayHello(String name, int age) {
        return "Hello, " + name + "! You are " + age + " years old.";
    }
}
