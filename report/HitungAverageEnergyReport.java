/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import core.ConnectionListener;
import java.util.HashSet;
import java.util.List;

import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.ModuleCommunicationBus;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.SimScenario;
import core.Tuple;
import core.UpdateListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import routing.ProphetRouter;
import routing.ProphetRouterBufferBrian;
import routing.ProphetRouterFuzzy;

/**
 * Node energy level report. Reports the energy level of all (or only some)
 * nodes every configurable-amount-of seconds. Writes reports only after the
 * warmup period.
 */
public class HitungAverageEnergyReport extends Report implements UpdateListener {

    public static final String ENERGY_REPORT_INTERVAL = "occupancyInterval";
    public static final int DEFAULT_ENERGY_REPORT_INTERVAL = 600;
    private int lastRecord = 0;
    private final int interval;
//    private final Map<DTNHost, List<Double>> energyVal;

    /**
     * Constructor. Reads the settings and initializes the report module.
     */
    public HitungAverageEnergyReport() {
        super();
        Settings s = new Settings();
//        this.energyVal = new HashMap<>();
        if (s.contains(ENERGY_REPORT_INTERVAL)) {
            interval = s.getInt(ENERGY_REPORT_INTERVAL);
        } else {
            interval = DEFAULT_ENERGY_REPORT_INTERVAL;
        }
    }

    @Override
    public void updated(List<DTNHost> hosts) {
        if (SimClock.getTime() - lastRecord >= interval) {
            for (DTNHost h : hosts) {
                ProphetRouterFuzzy router = (ProphetRouterFuzzy) h.getRouter();
                double value = router.getEnergy(h);
                if (h.toString().equals("P34")) {
                    printLine(lastRecord, value);
                }
            }
            lastRecord = (int) SimClock.getTime();
        }
    }

    private void printLine(int time, double value) {
        String tes = "";
        tes += time + "\t" + value + "\n";

        write(tes);
//        super.done();
//        for (DTNHost h : hosts) {
//            double value = (Double) h.getComBus().getProperty(routing.EnergyAwareRouter.ENERGY_VALUE_ID);
//
//            if (energyVal.containsKey(h)) {
//                List<Double> li = energyVal.get(h);
//                li.add(value);
//                energyVal.put(h, li);
//            } else {
//                List<Double> li = new ArrayList<>();
//                li.add(value);
//                energyVal.put(h, li);
//            }
//        }
    }

//    protected void writeInLine(String txt) {
//        if (out == null) {
//            init();
//        }
//        out.println(prefix + txt);
//    }
    public void done() {
//        int waktuInterval = 0;
//        for (int i = waktuInterval; waktuInterval < getSimTime(); i++) {
//            writeInLine("Detik " + Integer.toString(waktuInterval) + ',');
//            waktuInterval += 600;
//        }
//        write("");
//        String tes = "Menit\tEnergyValue\n";
//        for (Map.Entry<DTNHost, Tuple<Double, Double>> entry : energyVal.entrySet()) {
//            DTNHost key = entry.getKey();
//            double time = entry.getValue().getKey();
//            double value = entry.getValue().getValue();
////            List<Double> value = entry.getValue().getValue();
////            DTNHost a = entry.getKey();
////            Integer b = a.getAddress();
//            if (entry.getKey().toString().equals("P0")) {
//                tes += key + "\t" + value + "\n";
//            }
//        }

//        String tes = "";
//        List<DTNHost> hosts = SimScenario.getInstance().getHosts();
//        for (DTNHost h : hosts) {
////        double value = (Double) h.getComBus().getProperty(routing.ProphetRouterBufferBrian.ENERGY_VALUE_ID);
//            ProphetRouterFuzzy router = (ProphetRouterFuzzy) h.getRouter();
//            double value = router.getEnergy(h);
//            tes += h + " : " + value + "\n";
//        }
////        String tes = "" + SimScenario.getInstance().getHosts();
//        write(tes);
//        super.done();
    }
}
