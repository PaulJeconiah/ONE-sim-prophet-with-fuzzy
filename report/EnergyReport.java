/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package report;

import java.util.HashSet;
import java.util.List;

import core.DTNHost;
import core.Settings;
import core.SimClock;
import core.SimError;
import core.UpdateListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Node energy level report. Reports the energy level of all (or only some) 
 * nodes every configurable-amount-of seconds. Writes reports only after
 * the warmup period.
 */
public class EnergyReport extends Report implements UpdateListener {
	public static final String ENERGY_REPORT_INTERVAL = "occupancyInterval";
        /*Default value for the snapshot interval */
        public static final int DEFAULT_ENERGY_REPORT_INTERVAL = 3600;
        
        private double lastRecord = Double.MIN_VALUE;
        private int interval;
        
        private Map<DTNHost, List<Double>> energyVal;

    public EnergyReport() {
        super();
        Settings settings = getSettings();
        if(settings.contains(ENERGY_REPORT_INTERVAL)) {
            interval = settings.getInt(ENERGY_REPORT_INTERVAL);
        } else {
            interval = -1;
        }
        if (interval < 0) {
            interval = DEFAULT_ENERGY_REPORT_INTERVAL;
        }
    }
    
    public void updated(List<DTNHost> hosts) {
        if(isWarmup()) {
            return;
        }
        
        if(SimClock.getTime() - lastRecord >= interval) {
            lastRecord = SimClock.getTime();
            printLine(hosts);
        }
    }
    
    private void printLine(List<DTNHost> hosts) {
        for (DTNHost h : hosts) {
            double value = (Double)h.getComBus().getProperty(routing.EnergyAwareRouter.ENERGY_VALUE_ID);
            if(energyVal.containsKey(h)) {
                List<Double> li = energyVal.get(h);
                li.add(value);
                energyVal.put(h, li);
            } else {
                List<Double> li = new ArrayList<>();
                li.add(value);
                energyVal.put(h, li);
            }
        }
    }
    
    public void done() {
        int waktuInterval = 0;
        for(int i = waktuInterval; waktuInterval < getSimTime()/60; i++) {
            writeInLine("Menit " + Integer.toString(waktuInterval) + ',');
            waktuInterval += 60;
        }
        write("");
        for(Map.Entry<DTNHost, List<Double>> entry : energyVal.entrySet()) {
            DTNHost a = entry.getKey();
            Integer b= a.getAddress();
            write("Node " + b + ' ' + entry.getValue());
        }
    }

    private void writeInLine(String string) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
        
}
