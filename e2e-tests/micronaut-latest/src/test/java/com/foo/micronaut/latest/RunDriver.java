package com.foo.micronaut.latest;

import org.evomaster.client.java.controller.InstrumentedSutStarter;

public class RunDriver {
    public static void main(String[] args) {
        MicronautTestController controller = new MicronautTestController();
        InstrumentedSutStarter starter = new InstrumentedSutStarter(controller);
        starter.start();
    }
}
