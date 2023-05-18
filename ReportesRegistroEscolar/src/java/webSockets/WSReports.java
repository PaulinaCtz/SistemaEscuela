package webSockets;

import dataAccess.Reports;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint("/reportsEndpoint")
public class WSReports {

    @OnOpen
    public void onOpen(Session sesion){
        
    }
    
    @OnMessage
    public void onMessage(String received, Session sesion){
        Reports reports = new Reports();
        
        reports.addReport(received);
    }
    
}
