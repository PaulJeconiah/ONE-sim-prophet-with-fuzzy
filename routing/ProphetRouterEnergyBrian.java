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
import core.Settings;
import core.SimClock;
import core.Tuple;
import java.util.Random;

/**
 * Implementation of PRoPHET router as described in
 * <I>Probabilistic routing in intermittently connected networks</I> by Anders
 * Lindgren et al.
 */
public class ProphetRouterEnergyBrian extends EnergyAwareRouter {

    public static final double P_INIT = 0.75;
    public static final double DEFAULT_BETA = 0.25;
    public static final double GAMMA = 0.98;
//    public static final String INIT_ENERGY_S = "initEnergy";
    public static final String PROPHET_NS = "ProphetRouter";
    public static final String SECONDS_IN_UNIT_S = "secondsInTimeUnit";
    public static final String BETA_S = "beta";
    private int secondsInTimeUnit;
    private double beta;
    private double currentEnergy;
    private final double[] initEnergy;
    private Map<DTNHost, Double> preds;
    private double lastAgeUpdate;
    private static Random rng = null;

    public ProphetRouterEnergyBrian(Settings s) {
        super(s);
        this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
        Settings prophetSettings = new Settings(PROPHET_NS);
        secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
        beta = prophetSettings.contains(BETA_S) ? prophetSettings.getDouble(BETA_S) : DEFAULT_BETA;
        this.setEnergy(this.initEnergy);
        initPreds();
    }

    protected ProphetRouterEnergyBrian(ProphetRouterEnergyBrian r) {
        super(r);
        this.initEnergy = r.initEnergy;
        setEnergy(this.initEnergy);
        this.secondsInTimeUnit = r.secondsInTimeUnit;
        this.beta = r.beta;
        this.currentEnergy = r.currentEnergy;
        this.preds = new HashMap<>(r.preds);
        this.lastAgeUpdate = r.lastAgeUpdate;
        initPreds();
    }

    private void initPreds() {
        this.preds = new HashMap<>();
    }

    @Override
    public void changedConnection(Connection con) {
        if (con.isUp()) {
            DTNHost otherHost = con.getOtherNode(this.getHost());
            this.updateDeliveryPredFor(otherHost);
            this.updateTransitivePreds(otherHost);
        }
    }

    private void updateDeliveryPredFor(DTNHost host) {
        double oldValue = this.getPredFor(host);
        double newValue = oldValue + (1.0 - oldValue) * P_INIT;
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
        assert otherRouter instanceof ProphetRouterEnergyBrian : "PRoPHET only works with other routers of the same type";

        double pForHost = this.getPredFor(host);
        Map<DTNHost, Double> othersPreds = ((ProphetRouterEnergyBrian) otherRouter).getDeliveryPreds();

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
        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }
        tryOtherMessages();
    }

    @Override
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

    /**
     * Get the current energy level of the host.
     *
     * @param host
     * @return The current energy level
     */
    public double getEnergy(DTNHost host) {
        return (Double) host.getComBus().getProperty(routing.EnergyAwareRouter.ENERGY_VALUE_ID);
    }

//    private double getInitialEnergy(DTNHost host) {
//        return (Double) host.getComBus().getProperty(routing.EnergyAwareRouter.INIT_ENERGY_S);
//    }

    @Override
    public int receiveMessage(Message m, DTNHost from) {
        return super.receiveMessage(m, from);
    }

    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages = new ArrayList<>();
        Collection<Message> msgCollection = getMessageCollection();
        DTNHost thisHost = getHost();
        double thisEnergy = getEnergy(thisHost);

        // Iterasi melalui koneksi-koneksi yang dimiliki oleh simpul ini
        for (Connection connection : getConnections()) {
            DTNHost otherHost = connection.getOtherNode(this.getHost()); // Simpul yang terhubung
            ProphetRouterEnergyBrian otherRouter = (ProphetRouterEnergyBrian) otherHost.getRouter(); // Router dari simpul terhubung

            // Jika simpul terhubung sedang melakukan transfer atau memiliki energi yang lebih rendah, lanjutkan ke koneksi berikutnya
            if (!otherRouter.isTransferring()) {
                for (Message message : msgCollection) {
                    if (otherRouter.hasMessage(message.getId())) {
                        continue;
                    }
                    tryAllMessagesToAllConnections();
                    if (otherRouter.getPredFor(message.getTo()) > getPredFor(message.getTo())) {
                        if (thisEnergy < getEnergy(otherHost)) {
                            messages.add(new Tuple<Message, Connection>(message, connection)); // Tambahkan pesan ke daftar pesan yang akan dikirimkan
                        }
                    }
                }
            }
        }

        // Jika tidak ada pesan yang memenuhi syarat, kembalikan nilai null
        if (messages.isEmpty()) {
            return null;
        }

        // Urutkan daftar pesan berdasarkan probabilitas pengiriman
        messages.sort(new TupleComparator());
        // Kembalikan pesan pertama dalam daftar yang sudah diurutkan
        return tryMessagesForConnected(messages);	// try to send messages
    }

    /**
     * Comparator for Message-Connection-Tuples that orders the tuples by their
     * delivery probability by the host on the other side of the connection
     * (GRTRMax)
     */
    private class TupleComparator implements Comparator<Tuple<Message, Connection>> {

        public int compare(Tuple<Message, Connection> tuple1, Tuple<Message, Connection> tuple2) {
            // delivery probability of tuple1's message with tuple1's connection
            double p1 = ((ProphetRouterEnergyBrian) tuple1.getValue().getOtherNode(getHost()).getRouter()).getPredFor(tuple1.getKey().getTo());
            // -"- tuple2...
            double p2 = ((ProphetRouterEnergyBrian) tuple2.getValue().getOtherNode(getHost()).getRouter()).getPredFor(tuple2.getKey().getTo());

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
        this.ageDeliveryPreds();
        RoutingInfo top = super.getRoutingInfo();
        RoutingInfo ri = new RoutingInfo(this.preds.size() + " delivery prediction(s)");

        for (Map.Entry<DTNHost, Double> e : this.preds.entrySet()) {
            DTNHost host = e.getKey();
            Double value = e.getValue();
            ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", host, value)));
        }

        top.addMoreInfo(ri);
        return top;
    }

//    @Override
//    public MessageRouter replicate() {
//        return new ProphetRouterEnergyBrian(this);
//    }
//    public double getEnergy(DTNHost host) {
//        return this.currentEnergy;
//    }
    public Map<DTNHost, double[]> getEnergyPerInterval() {
        Map<DTNHost, double[]> energyMap = new HashMap<>();
        energyMap.put(this.getHost(), new double[]{this.currentEnergy});
        return energyMap;
    }

//    public Map<DTNHost, double[] > getTestEnergi(){
//        Map<DTNHost, double[] > energyMap = new HashMap<>();
//        energyMap.put(this.getHost(), new double[] {this.currentEnergy});
//        return energyMap;
//}
}
