package com.insightsystems.dal.crestron;

import com.avispl.symphony.api.common.error.NotAuthorizedException;
import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.ping.Pingable;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.*;

/**
 * Crestron AirMedia Device Adapter
 * Company: InSight Systems
 * @author Jayden (@JaydenL-Insight)
 * @version 1.4.3
 */
public class AirMedia extends RestCommunicator implements Monitorable, Pingable, Controller {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AirMedia(){
        this.setAuthenticationScheme(AuthenticationScheme.None);
        this.setTrustAllCertificates(true);
        this.setContentType("application/json");
    }

    @Override
    protected void authenticate() throws Exception {
        //Attempt user login with credentials.
        try {
            doPost("userlogin.html", "login=" + this.getLogin() + "&passwd=" + this.getPassword());
        } catch (CommandFailureException e){
            if (e.getStatusCode() == 403){
                throw new NotAuthorizedException("Username and password combination is invalid",e);
            }
            throw e;
        }
    }

    @Override
    public List<Statistics> getMultipleStatistics() throws Exception {
        Map<String,String> stats = new HashMap<>();
        ExtendedStatistics statistics = new ExtendedStatistics();
        List<AdvancedControllableProperty> controls = new ArrayList<>();

        this.authenticate();

        String devResponse = doGet("Device/");
        JsonNode json = objectMapper.readTree(devResponse);

        stats.put("Device#pufVersion", json.at("/Device/DeviceInfo/PufVersion").asText());
        stats.put("Device#serialNumber",json.at("/Device/DeviceInfo/SerialNumber").asText());
        stats.put("Device#macAddress",json.at("/Device/DeviceInfo/MacAddress").asText());
        stats.put("Device#model",json.at("/Device/DeviceInfo/Model").asText());
        stats.put("Device#rebootReason",json.at("/Device/DeviceInfo/RebootReason").asText());
        stats.put("Device#uptime",json.at("/Device/DeviceSpecific/UpTime").asText().replace("The system has been running for ",""));
        stats.put("Device#autoRoutingEnabled",json.at("/Device/SourceSelectionConfiguration/IsAutoRoutingEnabled").asText());
        stats.put("Device#miracastEnabled",json.at("/Device/AirMedia/Miracast/IsEnabled").asText());
        stats.put("Device#autoUpdateEnabled",json.at("/Device/AutoUpdateMaster/IsEnabled").asText());
        stats.put("Device#airmediaEnabled",json.at("/Device/AirMedia/IsEnabled").asText());
        stats.put("Device#loginCode",json.at("/Device/AirMedia/loginCode").asText());
        stats.put("Device#flexModeEnabled",json.at("/Device/App/Config/General/IsFlexModeEnabled").asText());
        stats.put("RoomName",json.at("/Device/App/Config/General/RoomName").asText());
        if (json.at("/Device/AirMedia/Miracast/IsEnabled").asText().equals("true")){
            stats.put("Device#miracastDongleStatus",json.at("/Device/AirMedia/Miracast/WifiDongleStatus").asText());
        }

        ArrayNode inputs = (ArrayNode) json.at("/Device/AudioVideoInputOutput/Inputs");
        int hdmiCount = 1;
        for (int i = 0; i < inputs.size(); i++){
            JsonNode port = inputs.get(i).at("/Ports").get(0);
            String inputName = port.at("/PortType").asText().equalsIgnoreCase("HDMI") ? "Hdmi " + hdmiCount++ + " Input": port.at("/PortType").asText() + " Input";
            stats.put(inputName + "#SourceDetected",port.at("/IsSourceDetected").asText());
            stats.put(inputName + "#SyncDetected",port.at("/IsSyncDetected").asText());
            stats.put(inputName + "#Resolution",port.at("/HorizontalResolution").asText() + "x" + port.at("/VerticalResolution").asText() + "@" + port.at("/FramesPerSecond").asText() + " " + port.at("/Audio/Digital/Format").asText());
            stats.put(inputName + "#HdcpSupportEnabled",port.at("/Hdmi/IsHdcpSupportEnabled").asText());
            stats.put(inputName + "#SourceHdcpActive",port.at("/Hdmi/IsSourceHdcpActive").asText());
            stats.put(inputName + "#HdcpState",port.at("/Hdmi/HdcpState").asText());
        }

        ArrayNode outputs = (ArrayNode) json.at("/Device/AudioVideoInputOutput/Outputs");
        hdmiCount = 1;
        for (int i = 0; i < outputs.size(); i++){
            JsonNode port = outputs.get(i).at("/Ports").get(0);
            String outputName = port.at("/PortType").asText().equalsIgnoreCase("HDMI") ? "Hdmi " + hdmiCount++ + " Output" : port.at("/PortType").asText() + " Output";
            if (port.at("/Hdmi/IsOutputDisabled").asText().equals("true")){
                stats.put(outputName + "#Disabled","true");
            }else {

                stats.put(outputName + "#SinkConnected", port.at("/IsSinkConnected").asText());
                stats.put(outputName + "#HdcpForceDisabled", port.at("/Hdmi/IsHdcpForceDisabled").asText());
                stats.put(outputName + "#Resolution", port.at("/HorizontalResolution").asText() + "x" + port.at("/VerticalResolution").asText() + "@" + port.at("/FramesPerSecond").asText() + " " + port.at("/Audio/Digital/Format").asText());
                stats.put(outputName + "#DisabledByHdcp", port.at("/Hdmi/DisabledByHdcp").asText());
                stats.put(outputName + "#HdcpTransmitterMode", port.at("/Hdmi/HdcpTransmitterMode").asText());
                stats.put(outputName + "#HdcpState", port.at("/Hdmi/HdcpState").asText());
            }
        }

        //Create advanced Controls
        createButton("Device#reboot","Reboot","Rebooting...",10000L,stats,controls);
        createButton("Device#synchroniseTime","Synchronise Time","Syncing now...",20000L,stats,controls);

        statistics.setStatistics(stats);
        statistics.setControllableProperties(controls);
        return Collections.singletonList(statistics);
    }

    @Override
    public void controlProperty(ControllableProperty cp) throws Exception {
        String property = cp.getProperty();
        String value = String.valueOf(cp.getValue());

        switch(property){
            case "Device#reboot":
                doPost("Device/DeviceOperations", "{\"Device\":{\"DeviceOperations\":{\"Reboot\":true}}}");
                break;
            case "Device#synchroniseTime":
                try {
                    doPost("Device/SystemClock", "{\"Device\":{\"SystemClock\":{\"Sntp\":{\"ForceSynchronizationNow\":true}}}}");
                } catch (Exception ignored){} //Request will always timeout with any reasonable value, lets just ignore the response

                break;
            default:
                if (this.logger.isWarnEnabled())
                    this.logger.warn("Control property is invalid: " + property);
        }
    }

    @Override
    public void controlProperties(List<ControllableProperty> list) throws Exception {
        for (ControllableProperty controllableProperty : list)
            controlProperty(controllableProperty);
    }

    public void createButton(String name, String label,String labelPressed,long grace,Map<String,String>stats,List<AdvancedControllableProperty> controls){
        AdvancedControllableProperty.Button button = new AdvancedControllableProperty.Button();
        button.setGracePeriod(10000L);
        button.setLabel(label);
        button.setLabelPressed(labelPressed);
        controls.add(new AdvancedControllableProperty(name,new Date(),button,"0"));
        stats.put(name,"0");
    }

    @Override
    protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) throws Exception {
        if (uri.equals("userlogin.html")){
            headers.set("Content-Type","application/x-www-form-urlencoded");
            headers.add("Host",this.getHost());
            headers.add("Connection", "keep-alive");
            headers.add("sec-ch-ua","\" Not;A Brand\";v=\"99\", \"Google Chrome\";v=\"91\", \"Chromium\";v=\"91\"" );
            headers.add("Accept", "*/*");
            headers.add("X-Requested-With", "XMLHttpRequest");
            headers.add("sec-ch-ua-mobile", "?0");
            headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            headers.add("Origin", "https://"+this.getHost());
            headers.add("Sec-Fetch-Site", "same-origin");
            headers.add("Sec-Fetch-Mode", "cors");
            headers.add("Sec-Fetch-Dest", "empty");
            headers.add("Referer", "https://"+this.getHost()+"/userlogin.html");
        }
        return headers;
    }

    public static void main(String[] args) throws Exception {
        AirMedia am = new AirMedia();
        am.setHost("192.168.0.112");
        am.setLogin("admin");
        am.setPassword("19881988");
        am.setProtocol("https");
        am.init();
        ((ExtendedStatistics)am.getMultipleStatistics().get(0)).getStatistics().forEach((k,v) -> System.out.println(k + " : " + v));

    }
}
