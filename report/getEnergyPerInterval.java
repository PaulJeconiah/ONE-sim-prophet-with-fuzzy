package report;

import core.*;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import routing.ActiveRouter;
import routing.EnergyPerInterval;
public class getEnergyPerInterval extends Report {
    private int interval;
    private double lastRecordTime;

    public getEnergyPerInterval() {
        init();
        interval = 60; // Interval set to 3 minutes (in seconds) -> optional for user to change
        lastRecordTime = -1; // Set to an invalid value initially
    }

    protected void init() {
        super.init();
    }

    public void done() {
        double currentTime = SimClock.getTime();
        if (currentTime - lastRecordTime >= interval) {
            lastRecordTime = currentTime;
            recordEnergyPerInterval();
        }


        super.done();
    }

    private void recordEnergyPerInterval() {
        String stats = "";
        List<DTNHost> hosts = SimScenario.getInstance().getHosts();
        // Iterate through hosts to collect energy data
        for (DTNHost host : hosts) {
            if (host.getRouter() instanceof ActiveRouter) {
                ActiveRouter router = (ActiveRouter) host.getRouter();
                if (router instanceof EnergyPerInterval) {
                    Map<DTNHost, double[]> energyMap = ((EnergyPerInterval) router).getEnergyPerInterval();
                    if (energyMap != null && energyMap.containsKey(host)) {
                        double[] energy = energyMap.get(host);
                        if (energy != null && energy.length > 0) {
                            stats += energy[0] + ", \n";
                        }
                    }
                }
            }
        }

        // Write the collected energy data to a file
        write(stats.toString());
    }
//    private void writeToFile(String data) {
//        String filePath = "D:\\test";
//        try (FileWriter writer = new FileWriter(filePath, true)) { // Append mode
//            writer.write(data);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}
