/*
 * $Header$
 *
 * Copyright (c) 2008 FXCM, LLC. All Rights Reserved.
 * 32 Old Slip, 10th Floor, New York, NY 10005 USA
 *
 * THIS SOFTWARE IS THE CONFIDENTIAL AND PROPRIETARY INFORMATION OF
 * FXCM, LLC. ("CONFIDENTIAL INFORMATION"). YOU SHALL NOT DISCLOSE
 * SUCH CONFIDENTIAL INFORMATION AND SHALL USE IT ONLY IN ACCORDANCE
 * WITH THE TERMS OF THE LICENSE AGREEMENT YOU ENTERED INTO WITH
 * FXCM.
 *
 * $History: $
 */

import quickfix.Message;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.MessageCracker;
import quickfix.fix44.MessageFactory;
import quickfix.fix44.SecurityStatus;
import quickfix.fix44.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

/**
 *
 */
public class FIXTradingTester {
    private static final boolean AUTH_ON_LOGIN = false;

    public static void main(String[] args) {
        if (args.length == 1) {
            String config = args[0];
            try {
                FileInputStream fileInputStream = new FileInputStream(config);
                SessionSettings settings = new SessionSettings(fileInputStream);
                String username = settings.getString("username");
                String password = settings.getString("password");
                String pin = settings.getDefaultProperties().getProperty("pin", null);

                MyApp app = new MyApp(username, password, pin);
                MessageStoreFactory storeFactory = new MemoryStoreFactory();
                LogFactory logFactory = new ScreenLogFactory(settings);
                MessageFactory messageFactory = new MessageFactory();
                SocketInitiator initiator = new SocketInitiator(app,
                                                                storeFactory,
                                                                settings,
                                                                logFactory,
                                                                messageFactory);
                initiator.start();
                BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                //noinspection InfiniteLoopStatement
                while (true) {
                    String str = in.readLine();
                    if (str != null) {
                        if ("o".equalsIgnoreCase(str.trim())) {
                            app.sendOrder();
                        } else if ("ol".equalsIgnoreCase(str.trim())) {
                            app.sendOrderList();
                        } else if ("eo".equalsIgnoreCase(str.trim())) {
                            app.sendEntryOrder();
                        } else if ("or".equalsIgnoreCase(str.trim())) {
                            app.setOpenRange();
                        } else if ("oto".equalsIgnoreCase(str.trim())) {
                            app.setOTO();
                        } else if ("pq".equalsIgnoreCase(str.trim())) {
                            app.setPreviouslyQuoted();
                        } else if ("m".equalsIgnoreCase(str.trim())) {
                            app.setPrintMDS(!app.isPrintMDS());
                        } else if ("rr".equalsIgnoreCase(str.trim())) {
                            app.sendResendRequest();
                        } else if ("ci".equalsIgnoreCase(str.trim())) {
                            app.getAccounts();
                        } else if ("tr".equalsIgnoreCase(str.trim())) {
                            app.sendTestRequest();
                        } else if ("ur".equalsIgnoreCase(str.trim())) {
                            app.sendUserRequest();
                        } else if ("osr".equalsIgnoreCase(str.trim())) {
                            app.sendOrderStatusRequest();
                        } else if ("l".equalsIgnoreCase(str.trim())) {
                            app.send(new Logout());
                        } else if ("sub".equalsIgnoreCase(str.trim())) {
                            app.sendMarketDataRequest(SubscriptionRequestType.SNAPSHOT_UPDATES);
                        } else if ("unsub".equalsIgnoreCase(str.trim())) {
                            app.sendMarketDataRequest(SubscriptionRequestType.DISABLE_PREVIOUS_SNAPSHOT_UPDATE_REQUEST);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Error: Supply configuration file");
        }
    }

    private static class MyApp extends MessageCracker implements Application {
        private static final String mInstrument = "EUR/USD";
        private ClOrdID mClOrdID;
        private CollInquiryID mColInquiryID;
        private CollateralReport mCollateralReport;
        private String mMsgReqID;
        private long mMsgSent;
        private boolean mOTO;
        private boolean mOpenRange;
        private String mPIN;
        private String mPassword;
        private boolean mPreviouslyQuoted;
        private boolean mPrintMDS;
        private long mRequestID;
        private SessionID mSessionID;
        private TradingSessionStatus mSessionStatus;
        private Date mStartSession;
        private Calendar mUTCCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        private String mUsername;

        private MyApp(String aUsername, String aPassword, String aPIN) {
            mUsername = aUsername;
            mPassword = aPassword;
            mPIN = aPIN;
            mPrintMDS = false;
        }

        @Override
        public void fromAdmin(Message aMessage, SessionID aSessionID) {
            try {
                crack(aMessage, aSessionID);
                System.out.println(LocalDateTime.now() + " :: " + aMessage);
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }

        @Override
        public void fromApp(Message aMessage, SessionID aSessionID) {
            try {
                crack(aMessage, aSessionID);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void getAccounts() {
            System.out.println("GETTING ACCOUNTS");
            CollateralInquiry msg = new CollateralInquiry();
            mColInquiryID = new CollInquiryID(String.valueOf(nextID()));
            msg.set(mColInquiryID);
            msg.set(new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT_UPDATES));

            CollateralInquiry.NoPartyIDs noPartyIDs = new CollateralInquiry.NoPartyIDs();
            noPartyIDs.set(new PartyID("FXCM ID"));
            noPartyIDs.set(new PartyIDSource('D'));
            noPartyIDs.set(new NoPartySubIDs(3));
            CollateralInquiry.NoPartyIDs.NoPartySubIDs sub = new CollateralInquiry.NoPartyIDs.NoPartySubIDs();
            sub.set(new PartySubID(mUsername));
            sub.set(new PartySubIDType(26));
            noPartyIDs.addGroup(sub);

            msg.addGroup(noPartyIDs);
            send(msg);
        }

        private void getClosedPositions() {
            try {
                RequestForPositions rfp = new RequestForPositions();
                rfp.set(new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT_UPDATES));
                rfp.set(new PosReqType(PosReqType.TRADES));
                rfp.set(new Account("ALL"));
                rfp.set(new TransactTime());
                rfp.set(new AccountType(AccountType.ACCOUNT_IS_CARRIED_ON_NON_CUSTOMER_SIDE_OF_BOOKS_AND_IS_CROSS_MARGINED));
                rfp.set(new PosReqID(String.valueOf(nextID())));
                rfp.set(new ClearingBusinessDate(getDate()));
                send(rfp);
            } catch (Exception aException) {
                aException.printStackTrace();
            }
        }

        private String getDate() {
            String year = String.valueOf(mUTCCal.get(Calendar.YEAR));
            int iMonth = mUTCCal.get(Calendar.MONTH) + 1;
            String month = iMonth <= 9 ? "0" + iMonth : String.valueOf(iMonth);
            int iDay = mUTCCal.get(Calendar.DAY_OF_MONTH);
            String day = iDay <= 9 ? "0" + iDay : String.valueOf(iDay);
            return year + month + day;
        }

        private void getOpenPositions() {
            try {
                RequestForPositions rfp = new RequestForPositions();
                rfp.set(new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT_UPDATES));
                rfp.set(new PosReqType(PosReqType.POSITIONS));
                rfp.set(new Account("ALL"));
                rfp.set(new TransactTime());
                rfp.set(new AccountType(AccountType.ACCOUNT_IS_CARRIED_ON_NON_CUSTOMER_SIDE_OF_BOOKS_AND_IS_CROSS_MARGINED));
                rfp.set(new PosReqID(String.valueOf(nextID())));
                rfp.set(new ClearingBusinessDate(getDate()));
                send(rfp);
            } catch (Exception aException) {
                aException.printStackTrace();
            }
        }

        private void getOrders() {
            MassStatusReqID reqID = new MassStatusReqID(String.valueOf(nextID()));
            MassStatusReqType reqType = new MassStatusReqType(MassStatusReqType.STATUS_FOR_ALL_ORDERS);
            OrderMassStatusRequest oms = new OrderMassStatusRequest(reqID, reqType);
            send(oms);
        }

        public boolean isPrintMDS() {
            return mPrintMDS;
        }

        public void setPrintMDS(boolean aPrintMDS) {
            mPrintMDS = aPrintMDS;
        }

        private String nextID() {
            return UUID.randomUUID().toString();
        }

        @Override
        public void onCreate(SessionID aSessionID) {
            mSessionID = aSessionID;
        }

        @Override
        public void onLogon(SessionID aSessionID) {
            System.out.println("got logon " + aSessionID);
            mStartSession = new Date();
            if (AUTH_ON_LOGIN) {
                sendTradingSessionStatusRequest();
            } else {
                sendUserRequest();
            }
        }

        @Override
        public void onLogout(SessionID aSessionID) {
            System.out.println("\n\ngot logout " + aSessionID);
            //System.out.println("StartSession = " + mStartSession);
            //System.out.println("StopSession = " + new Date());
            //System.out.println("\n\n");
        }

        @Override
        public void onMessage(UserResponse aUserResponse, SessionID aSessionID)
            throws FieldNotFound {
            //System.out.println("ROUNDTRIP MS : " + (System.currentTimeMillis() - mMsgSent));
            System.out.println("<< UserResponse = " + aUserResponse);
            if (aUserResponse.getInt(UserStatus.FIELD) == UserStatus.LOGGED_IN) {
                sendTradingSessionStatusRequest();
            }
        }

        private void sendTradingSessionStatusRequest() {
            TradingSessionStatusRequest msg = new TradingSessionStatusRequest();
            msg.set(new TradSesReqID("TSSR REQUEST ID " + nextID()));
            msg.set(new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT_UPDATES));
            send(msg);
        }

        @Override
        public void onMessage(CollateralInquiryAck aCollateralInquiryAck,
                              SessionID aSessionID) {
            //System.out.println("<< Collateral Inquiry Ack = " + aCollateralInquiryAck);
        }

        @Override
        public void onMessage(CollateralReport aCollateralReport,
                              SessionID aSessionID) throws FieldNotFound {
            System.out.println(LocalDateTime.now() + " :: Collateral Report = " + aCollateralReport);
            if (mColInquiryID.equals(aCollateralReport.getCollInquiryID())) {
                if (mCollateralReport != null) return;
                mCollateralReport = aCollateralReport;
                SecurityListRequest slr = new SecurityListRequest();
                slr.set(new SecurityReqID(String.valueOf(nextID())));
                slr.set(new SecurityListRequestType(SecurityListRequestType.ALL_SECURITIES));
                slr.addGroup(aCollateralReport.getGroup(1, new CollateralReport.NoPartyIDs()));
                send(slr);
            }
        }

        @Override
        public void onMessage(SecurityList message,
                              SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
            System.out.println("message = " + message);
        }

        @Override
        public void onMessage(BusinessMessageReject aBusinessMessageReject,
                              SessionID aSessionID) {
            System.out.println("<< Business Message Reject = " + aBusinessMessageReject);
        }

        @Override
        public void onMessage(MarketDataSnapshotFullRefresh aMarketDataSnapshotFullRefresh,
                              SessionID aSessionID) throws FieldNotFound {
            System.out.println("<< MDS = " + aMarketDataSnapshotFullRefresh);
        }

        @Override
        public void onMessage(ExecutionReport aExecutionReport, SessionID aSessionID) throws FieldNotFound {
            if (aExecutionReport.getOrdStatus().getValue() == OrdStatus.REJECTED) {
                System.out.println("<< REJECT EXE RPT = " + aExecutionReport);
            }
            System.out.println(LocalDateTime.now() + " :: Execution Report = " + aExecutionReport);
        }

        @Override
        public void onMessage(PositionReport aPositionReport,
                              SessionID aSessionID) {
            try {
                System.out.println("9042=" + aPositionReport.getString(9042));
            } catch (FieldNotFound aFieldNotFound) {
                aFieldNotFound.printStackTrace();
            }
            System.out.println("<< Position Report = " + aPositionReport);
        }

        @Override
        public void onMessage(RequestForPositionsAck aRequestForPositionsAck,
                              SessionID aSessionID) {
            System.out.println("<< Request For Positions Ack = " + aRequestForPositionsAck);
        }

        @Override
        public void onMessage(TradingSessionStatus aSessionStatus,
                              SessionID aSessionID) {
            mSessionStatus = aSessionStatus;
            System.out.println("<< Trading Station Status = " + aSessionStatus);
            getAccounts();
            //getOrders();
            //getOpenPositions();
            //getClosedPositions();
            //sendMarketDataRequest(SubscriptionRequestType.SNAPSHOT_UPDATES);
        }

        @Override
        public void onMessage(Heartbeat aMessage, SessionID aSessionID)
            throws FieldNotFound {
            if (mMsgReqID.equals(aMessage.getTestReqID().getValue())) {
                System.out.println("<< Heartbeat = " + aMessage);
                System.out.println("ROUNDTRIP MS : " + (System.currentTimeMillis() - mMsgSent));
            }
        }

        @Override
        public void onMessage(MarketDataIncrementalRefresh message, SessionID sessionID) {
            System.out.println("<< MarketDataIncrementalRefresh = " + message);
        }

        @Override
        public void onMessage(MarketDataRequestReject aReject,
                              SessionID aSessionID) {
            System.out.println("<< MarketDataRequestReject = " + aReject);
        }

        @Override
        public void onMessage(SecurityStatus aSecurityStatus,
                              SessionID aSessionID) {
            System.out.println("<< SecurityStatus = " + aSecurityStatus);
        }

        @Override
        public void onMessage(Logout aLogout, SessionID aSessionID) {
            try {
                String val = aLogout.getText().getValue().split("expecting")[1].split("but")[0];
                System.out.println("next seq = " + val);
                Session.lookupSession(aSessionID).setNextSenderMsgSeqNum(Integer.parseInt(val.trim()) - 1);
            } catch (Exception aE) {
                //aE.printStackTrace();
            }
            System.out.println("got logout = " + aLogout);
        }

        private void send(Message aMessage) {
            try {
                System.out.println("sending = " + aMessage);
                Session.sendToTarget(aMessage, mSessionID);
            } catch (Exception aException) {
                aException.printStackTrace();
            }
        }

        public void sendEntryOrder() {
            try {
                sendEntryOrder(mCollateralReport, mSessionID);
            } catch (FieldNotFound aFieldNotFound) {
                aFieldNotFound.printStackTrace();
            }
        }

        private void sendEntryOrder(CollateralReport aCollateralReport,
                                    SessionID aSessionID) throws FieldNotFound {
            NewOrderSingle order = new NewOrderSingle(new ClOrdID(aSessionID
                                                                      + "-"
                                                                      + System.currentTimeMillis()
                                                                      + "-"
                                                                      + nextID()),
                                                      new Side(Side.BUY),
                                                      new TransactTime(),
                                                      new OrdType(OrdType.LIMIT));
            System.out.println("-- creating order with account = " + aCollateralReport.getAccount());
            order.set(aCollateralReport.getAccount());
            order.set(new Symbol(mInstrument));
            order.set(new OrderQty(mCollateralReport.get(new Quantity()).getValue()));
            order.set(new Price(3.4));
            order.set(new TimeInForce(TimeInForce.GOOD_TILL_CANCEL));
            order.set(new SecondaryClOrdID("fix multi session test"));
            /*
            order.setInt(211, 0);
            order.setInt(835, 0);
            order.setInt(836, 0);
            order.setInt(1094, 0);
            */
            order.setInt(9061, 22);
            System.out.println(" >> sending order = " + order);
            send(order);
        }

        public void sendMarketDataRequest(char aSubscriptionRequestType) {
            try {
                System.out.println("sending mdr");
                SubscriptionRequestType subReqType = new SubscriptionRequestType(aSubscriptionRequestType);
                MarketDataRequest mdr = new MarketDataRequest();
                mdr.set(new MDReqID(String.valueOf(nextID())));
                mdr.set(subReqType);
                mdr.set(new MarketDepth(1));
                mdr.set(new MDUpdateType(MDUpdateType.FULL_REFRESH));

                MarketDataRequest.NoMDEntryTypes types = new MarketDataRequest.NoMDEntryTypes();
                types.set(new MDEntryType(MDEntryType.BID));
                mdr.addGroup(types);

                types = new MarketDataRequest.NoMDEntryTypes();
                types.set(new MDEntryType(MDEntryType.OFFER));
                mdr.addGroup(types);

                types = new MarketDataRequest.NoMDEntryTypes();
                types.set(new MDEntryType(MDEntryType.TRADING_SESSION_HIGH_PRICE));
                mdr.addGroup(types);

                types = new MarketDataRequest.NoMDEntryTypes();
                types.set(new MDEntryType(MDEntryType.TRADING_SESSION_LOW_PRICE));
                mdr.addGroup(types);

                /*
                MarketDataRequest.NoRelatedSym symbol = new MarketDataRequest.NoRelatedSym();
                symbol.set(new Symbol("EUR/USD"));
                mdr.addGroup(symbol);
                */
                /*

                symbol = new MarketDataRequest.NoRelatedSym();
                symbol.set(new Symbol("EUR/JPY"));
                mdr.addGroup(symbol);
                */

                int max = mSessionStatus.getField(new IntField(NoRelatedSym.FIELD)).getValue();
                for (int i = 1; i <= max; i++) {
                    SecurityList.NoRelatedSym relatedSym = new SecurityList.NoRelatedSym();
                    SecurityList.NoRelatedSym group = (SecurityList.NoRelatedSym) mSessionStatus.getGroup(i,
                                                                                                          relatedSym);
                    MarketDataRequest.NoRelatedSym symbol = new MarketDataRequest.NoRelatedSym();
                    symbol.set(group.getInstrument());
                    mdr.addGroup(symbol);
//                    SecurityStatusReqID id = new SecurityStatusReqID(String.valueOf(nextID()));
//                    SecurityStatusRequest ssr = new SecurityStatusRequest(id, subReqType);
//                    ssr.set(group.getInstrument());
//                    send(ssr);
                }

                send(mdr);
            } catch (Exception aException) {
                aException.printStackTrace();
            }
        }

        public void sendMassOrderStatusRequest() {
            try {
                OrderMassStatusRequest oh = new OrderMassStatusRequest();
                oh.set(new MassStatusReqID(mSessionID
                                               + "-"
                                               + System.currentTimeMillis()
                                               + "-"
                                               + nextID()));
                oh.set(new MassStatusReqType(MassStatusReqType.STATUS_FOR_ALL_ORDERS));
                oh.set(mCollateralReport.getAccount());
                send(oh);
            } catch (Exception aFieldNotFound) {
                aFieldNotFound.printStackTrace();
            }
        }

        public void sendOrder() {
            try {
                mMsgSent = System.currentTimeMillis();
                sendOrder(mCollateralReport, mSessionID);
            } catch (FieldNotFound aFieldNotFound) {
                aFieldNotFound.printStackTrace();
            }
        }

        private void sendOrder(CollateralReport aCollateralReport,
                               SessionID aSessionID) throws FieldNotFound {
            mClOrdID = new ClOrdID(aSessionID
                                       + "-"
                                       + System.currentTimeMillis()
                                       + "-"
                                       + nextID());
            NewOrderSingle order = new NewOrderSingle(mClOrdID,
                                                      new Side(Side.BUY),
                                                      new TransactTime(),
                                                      new OrdType(OrdType.MARKET));
            //System.out.println("-- creating order with account = " + aCollateralReport.getAccount());
            order.setString(1, "0");
            order.set(new Symbol(mInstrument));
            order.set(new OrderQty(10000));
            order.set(new TimeInForce(TimeInForce.IMMEDIATE_OR_CANCEL));
            order.set(new SecondaryClOrdID("ASDF__ASDF"));
            //order.setString(Price.FIELD, "1.001");
            //order.setString(StopPx.FIELD, "1.8888");
            System.out.println(" >> sending order = " + order);
            send(order);
        }

        public void sendOrderList() {
            try {
                sendOrderList(mCollateralReport, mSessionID);
            } catch (FieldNotFound aFieldNotFound) {
                aFieldNotFound.printStackTrace();
            }
        }

        private void sendOrderList(CollateralReport aCollateralReport,
                                   SessionID aSessionID) throws FieldNotFound {
            NewOrderList list = new NewOrderList();
            list.set(new ListID("testlistid"));
            list.set(new BidType(BidType.NO_BIDDING_PROCESS));
            int max = 2;
            for (int i = 0; i < max; i++) {
                NewOrderList.NoOrders order = new NewOrderList.NoOrders();
                System.out.println("-- creating order with account = " + aCollateralReport.getAccount());
                order.set(new ClOrdID(aSessionID
                                          + "-"
                                          + System.currentTimeMillis()
                                          + "-"
                                          + nextID()));
                order.set(new ListSeqNo(i));
                order.set(new Side(Side.BUY));
                order.set(new OrderQty(aCollateralReport.get(new Quantity()).getValue()));
                order.set(new TransactTime());
                order.set(new OrdType(OrdType.MARKET));
                order.set(new Symbol(mInstrument));
                order.set(aCollateralReport.getAccount());
                order.addGroup(aCollateralReport.getGroup(1, new CollateralReport.NoPartyIDs()));
                order.set(new TimeInForce(TimeInForce.IMMEDIATE_OR_CANCEL));
                order.set(new SecondaryClOrdID("fix multi session test"));
                list.addGroup(order);
            }
            list.set(new TotNoOrders(max));
            System.out.println(" >> sending order list = " + list);
            send(list);
        }

        public void sendOrderStatusRequest() {
            try {
                OrderStatusRequest osr = new OrderStatusRequest();
                osr.set(mCollateralReport.getAccount());
                osr.set(new OrderID("13800433"));
                send(osr);
            } catch (Exception aFieldNotFound) {
                aFieldNotFound.printStackTrace();
            }
        }

        public void sendRFP() {
            RequestForPositions rfp = new RequestForPositions();
            rfp.set(new SubscriptionRequestType(SubscriptionRequestType.SNAPSHOT_UPDATES));
            rfp.set(new PosReqType(PosReqType.POSITIONS));
            rfp.set(new Account("ALL"));
            rfp.set(new TransactTime());
            rfp.set(new AccountType(AccountType.ACCOUNT_IS_CARRIED_ON_NON_CUSTOMER_SIDE_OF_BOOKS_AND_IS_CROSS_MARGINED));
            rfp.set(new PosReqID(String.valueOf(nextID())));
            rfp.set(new ClearingBusinessDate(getDate()));
            send(rfp);
        }

        private void sendResendRequest() {
            ResendRequest rr = new ResendRequest(new BeginSeqNo(1), new EndSeqNo(0));
            System.out.println("sending resend request rr = " + rr);
            send(rr);
        }

        public void sendTestRequest() {
            try {
                TestRequest req = new TestRequest();
                req.set(new TestReqID(String.valueOf(nextID())));
                send(req);
                mMsgReqID = req.getTestReqID().getValue();
                mMsgSent = System.currentTimeMillis();
            } catch (FieldNotFound aFieldNotFound) {
                aFieldNotFound.printStackTrace();
            }
        }

        private void sendUserRequest() {
            mMsgSent = System.currentTimeMillis();
            UserRequest ur = new UserRequest();
            ur.setString(UserRequestID.FIELD, "MY_FIX_REQID::" + nextID());
            ur.setString(Username.FIELD, mUsername);
            ur.setString(Password.FIELD, mPassword);

            if (mPIN != null) {
                Group params = new Group(9016, 9017);
                params.setString(9017, "PIN");
                params.setString(9018, mPIN);
                ur.addGroup(params);
            }

            ur.setInt(UserRequestType.FIELD, 1);
            System.out.println(">> Sending User Request " + ur);
            send(ur);
        }

        public void setOTO() {
            mOTO = true;
        }

        public void setOpenRange() {
            mOpenRange = true;
        }

        public void setPreviouslyQuoted() {
            mPreviouslyQuoted = true;
        }

        @Override
        public void toAdmin(Message aMessage, SessionID aSessionID) {
            System.out.println("aMessage = " + aMessage);
            if (AUTH_ON_LOGIN) {
                try {
                    if (aMessage.getHeader().getString(MsgType.FIELD).contentEquals(Logon.MSGTYPE)) {
                        aMessage.setString(Username.FIELD, mUsername);
                        aMessage.setString(Password.FIELD, mPassword);
                    }
                } catch (FieldNotFound aFieldNotFound) {
                    aFieldNotFound.printStackTrace();
                }
            }
        }

        @Override
        public void toApp(Message aMessage, SessionID aSessionID) {
        }
    }
}
