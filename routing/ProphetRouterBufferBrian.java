/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.ModuleCommunicationBus;
import core.ModuleCommunicationListener;
import core.NetworkInterface;
import core.Settings;
import core.SimClock;
import core.Tuple;
import java.util.Random;

/**
 * Implementation of PRoPHET router as described in
 * <I>Probabilistic routing in intermittently connected networks</I> by Anders
 * Lindgren et al.
 */
public class ProphetRouterBufferBrian extends ActiveRouter implements ModuleCommunicationListener{

    public static final double P_INIT = 0.75;
    public static final double DEFAULT_BETA = 0.25;
    public static final double GAMMA = 0.98;
    public static final String INIT_ENERGY_S = "initEnergy";
    public static final String PROPHET_NS = "ProphetRouter";
    public static final String SECONDS_IN_UNIT_S = "secondsInTimeUnit";
    public static final String BETA_S = "beta";
    private int secondsInTimeUnit;
    private double beta;
    private double currentEnergy;
    private final double[] initEnergy;
    private Map<DTNHost, Double> preds;
    
    private double lastScanUpdate;
    private double lastUpdate;
    private double warmupTime;
    private double scanEnergy;
    private double transmitEnergy;
    private double scanResponseEnergy;
    ModuleCommunicationBus comBus;
    public static final String ENERGY_VALUE_ID = "Energy.value";
    public static final String SCAN_ENERGY_S = "scanEnergy";
    public static final String TRANSMIT_ENERGY_S = "transmitEnergy";
    public static final String SCAN_RESPONSE_ENERGY_S = "scanResponseEnergy";
//    private Map<DTNHost, ArrayList<Double>> Energi;
    private double lastAgeUpdate;
    private static Random rng = null;

    public ProphetRouterBufferBrian(Settings s) {
        super(s);
        this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
        this.scanEnergy = s.getDouble(SCAN_ENERGY_S);
        this.scanResponseEnergy = s.getDouble(SCAN_RESPONSE_ENERGY_S);
        Settings prophetSettings = new Settings(PROPHET_NS);
        secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
        beta = prophetSettings.contains(BETA_S) ? prophetSettings.getDouble(BETA_S) : DEFAULT_BETA;
        this.setEnergy(this.initEnergy);
        initPreds();
    }

    protected ProphetRouterBufferBrian(ProphetRouterBufferBrian r) {
        super(r);
        this.secondsInTimeUnit = r.secondsInTimeUnit;
        this.beta = r.beta;
        this.initEnergy = r.initEnergy;
        this.scanEnergy = r.scanEnergy;
        this.scanResponseEnergy = r.scanResponseEnergy;
        this.transmitEnergy = r.transmitEnergy;
        this.comBus = null;
        this.currentEnergy = r.currentEnergy;
        this.preds = new HashMap<>(r.preds);
        this.lastAgeUpdate = r.lastAgeUpdate;
        initPreds();
    }

    private void initPreds() {
        this.preds = new HashMap();
    }

    @Override
    public void changedConnection(Connection con) {
        if (con.isUp()) {
            DTNHost otherHost = con.getOtherNode(getHost());
            updateDeliveryPredFor(otherHost);
            updateTransitivePreds(otherHost);
        }
    }

    private void updateDeliveryPredFor(DTNHost host) {
        double oldValue = this.getPredFor(host);
        double newValue = oldValue + (1.0 - oldValue) * 0.75;
        this.preds.put(host, newValue);
    }

    /**
     * Returns the current prediction (P) value for a host or 0 if entry for the
     * host doesn't exist.
     *
     * @param host The host to look the P for
     * @return the current P value
     */
    public double getPredFor(DTNHost host) {
        ageDeliveryPreds(); // make sure preds are updated before getting
        if (preds.containsKey(host)) {
            return preds.get(host);
        } else {
            return 0;
        }
    }

    private void updateTransitivePreds(DTNHost host) {
        MessageRouter otherRouter = host.getRouter();
        assert otherRouter instanceof ProphetRouterBufferBrian : "PRoPHET only works with other routers of the same type";

        double pForHost = this.getPredFor(host);
        Map<DTNHost, Double> othersPreds = ((ProphetRouterBufferBrian) otherRouter).getDeliveryPreds();

        for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
            if (e.getKey() != this.getHost()) {
                double pOld = this.getPredFor(e.getKey());
                double pNew = pOld + (1.0 - pOld) * pForHost * e.getValue() * this.beta;
                this.preds.put(e.getKey(), pNew);
            }
        }
    }

    private void ageDeliveryPreds() {
        double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / this.secondsInTimeUnit;
        if (timeDiff != 0.0) {
            double mult = Math.pow(GAMMA, timeDiff);
            for (Map.Entry<DTNHost, Double> e : this.preds.entrySet()) {
                e.setValue(e.getValue() * mult);
            }
            this.lastAgeUpdate = SimClock.getTime();
        }
    }

    private Map<DTNHost, Double> getDeliveryPreds() {
        this.ageDeliveryPreds();
        return this.preds;
    }

    @Override
    public void update() {
        super.update();
        reduceSendingAndScanningEnergy();
        
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

//        // Check if there is enough space in the buffer
//        if (getFreeBufferSize() <= 0) {
//            return; // buffer is full, cannot send any messages
//        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }
        tryOtherMessages();
    }

    protected void setEnergy(double[] range) {
        if (range.length == 1) {
            this.currentEnergy = range[0];
        } else {
            if (rng == null) {
                rng = new Random((long) ((int) (range[0] + range[1])));
            }

            this.currentEnergy = range[0] + rng.nextDouble() * (range[1] - range[0]);
        }

    }
    
    protected void reduceEnergy(double amount) {
        if (SimClock.getTime() < this.warmupTime) {
            return; // Does Nothing
        }

        comBus.updateDouble(ENERGY_VALUE_ID, -amount);
        if (this.currentEnergy < 0) {
            comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
        }
    }

    protected void reduceSendingAndScanningEnergy() {
        double simTime = SimClock.getTime();

        if (this.comBus == null) {
            this.comBus = getHost().getComBus();
            this.comBus.addProperty(ENERGY_VALUE_ID, this.currentEnergy);
            this.comBus.subscribe(ENERGY_VALUE_ID, this);
        }

        if (this.currentEnergy <= 0) {
            /* turn radio off */
            this.comBus.updateProperty(NetworkInterface.RANGE_ID, 0.0);
            return;
            /* no more energy to start new transfers */
        }

        if (simTime > this.lastUpdate && sendingConnections.size() > 0) {
            /* sending data */
            reduceEnergy((simTime - this.lastUpdate) * this.transmitEnergy);
        }
        this.lastUpdate = simTime;

        if (simTime > this.lastScanUpdate) {
            /* scanning at this update round */
            reduceEnergy(this.scanEnergy);
            this.lastScanUpdate = simTime;
        }
    }
    
    

    public double getEnergy(DTNHost host) {
        return this.currentEnergy;
    }

//    public int getFreeBufferS() {
//        return get;
//    }

    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages = new ArrayList<>();
        Collection<Message> msgCollection = getMessageCollection();
        DTNHost thisHost = getHost();
        double thisEnergy = getEnergy(thisHost);

        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(thisHost);
            ProphetRouterBufferBrian otherRouter = (ProphetRouterBufferBrian) other.getRouter();

            if (!otherRouter.isTransferring() && thisEnergy <= otherRouter.getEnergy(other)) {
                int freeBufferSize = otherRouter.getFreeBufferSize();
                for (Message m : msgCollection) {

                    if (otherRouter.hasMessage(m.getId())) {
                        continue;
                    }
                    tryAllMessagesToAllConnections();
                    if (otherRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
                        if (freeBufferSize > 0) {
                            messages.add(new Tuple<Message, Connection>(m, con));
                            freeBufferSize--; // Reduce the available buffer size
                        } 
                        break;
                    }
                }
            }
        }

        if (messages.size() == 0) {
            return null;
        }

        // Sort the messages based on their delivery probability
        Collections.sort(messages, new TupleComparator());

        // Return the message with the highest delivery probability
        return tryMessagesForConnected(messages);
    }

    /**
     * Comparator for Message-Connection-Tuples that orders the tuples by their
     * delivery probability by the host on the other side of the connection
     * (GRTRMax)
     */
    private class TupleComparator implements Comparator<Tuple<Message, Connection>> {

        public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
            // delivery probability of tuple1's message with tuple1's connection
            double p1 = ((ProphetRouterBufferBrian) tuple1.getValue().getOtherNode(getHost()).getRouter()).getPredFor(tuple1.getKey().getTo());
            // -"- tuple2...
            double p2 = ((ProphetRouterBufferBrian) tuple2.getValue().getOtherNode(getHost()).getRouter()).getPredFor(tuple2.getKey().getTo());

            // bigger probability should come first
            if (p2 - p1 == 0) {
                /* equal probabilities -> let queue mode decide */
                return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
            } else if (p2 - p1 < 0) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    @Override
    public RoutingInfo getRoutingInfo() {
        ageDeliveryPreds();
        RoutingInfo top = super.getRoutingInfo();
        RoutingInfo ri = new RoutingInfo(preds.size()
                + " delivery prediction(s)");

        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
            DTNHost host = e.getKey();
            Double value = e.getValue();

            ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
                    host, value)));
        }

        top.addMoreInfo(ri);
        return top;
    }

    public MessageRouter replicate() {
        ProphetRouterBufferBrian r = new ProphetRouterBufferBrian(this);
        return r;
    }
    
    @Override
    public void moduleValueChanged(String key, Object newValue) {
        this.currentEnergy = (Double) newValue;
    }

//    public Map<DTNHost, Integer> getBufferForEachHost() {
//        Map<DTNHost, Integer> bufferMap = new HashMap<>();
//        bufferMap.put(this.getHost(), this.getBufferSize());
//        return bufferMap;
//    }
//
//    @Override
//    public Map<DTNHost, double[]> getEnergyPerInterval() {
//        Map<DTNHost, double[]> energyMap = new HashMap<>();
//        energyMap.put(this.getHost(), new double[]{this.currentEnergy});
//        return energyMap;
//    }
}
