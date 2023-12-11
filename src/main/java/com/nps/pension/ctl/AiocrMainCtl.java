package com.nps.pension.ctl;

import com.nps.pension.svc.AiocrMainSvc;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;

@Controller
@RequestMapping("/api/v1/aiocr")
@CrossOrigin(origins = "*")
public class AiocrMainCtl {
  
  private static final Logger Logger = LoggerFactory.getLogger(AiocrMainCtl.class);
  
  @Resource(name = "aiocrMainSvc")
  private AiocrMainSvc aiocrMainSvc;

  @Value("${twinreader.output.deleteYn}")
  String deleteYn;
  
  @Value("${aipct.pension.async}")
  Boolean asyncYn;

  @Value("${server.ip}")
  String serverIP;

  @Value("${twinreader.port}")
  String twinPort;
  
  @RequestMapping(value="/", method = RequestMethod.GET)
  public String npsTest() {
    return "npsTest";
  }

  @RequestMapping(value="/demoWeb", method = RequestMethod.GET)
  public String demoWeb(
      @RequestParam(value = "requestId", required = false) String requestId
      , @RequestParam(value = "schemaNm", required = false) String schemaNm
      , Model model) {

    model.addAttribute("requestId", requestId);
    model.addAttribute("schemaNm", schemaNm);
    model.addAttribute("serverIP", serverIP);
    model.addAttribute("twinPort", twinPort);
    
    ArrayList<JSONObject> requestList = new ArrayList<>();
    File outputFolder = new File("/data/twinreader/data/output/");
    if(outputFolder.exists()) {
      File[] path = outputFolder.listFiles();
      for(int i=0; i<path.length; i++) {
        if(path[i].isDirectory()) {
          try {
            JSONObject obj = new JSONObject();
            
            obj.put("name", path[i].getPath().replace("/data/twinreader/data/output/", ""));
            obj.put("time", Files.getAttribute(path[i].toPath(), "creationTime").toString());
            
            requestList.add(obj);
          } catch(Exception error) {
            Logger.error("##### GET REQUEST LIST error : " + error.getMessage());
          }
        }
      }
    }
    model.addAttribute("requestList", requestList);
    
    ArrayList<JSONObject> schemaList = new ArrayList<>();
    File schemaFolder = new File("/data/twinreader/schema/");
    if(schemaFolder.exists()) {
      File[] path = schemaFolder.listFiles();
      for(int i=0; i<path.length; i++) {
        if(path[i].isFile()) {
          try {
            JSONObject obj = new JSONObject();
            
            obj.put("name", path[i].getPath().replace("/data/twinreader/schema/", ""));
            obj.put("time", Files.getAttribute(path[i].toPath(), "creationTime").toString());
            
            schemaList.add(obj);
          } catch(Exception error) {
            Logger.error("##### GET SCHEMA LIST error : " + error.getMessage());
          }
        }
      }
    }
    model.addAttribute("schemaList", schemaList);
    

    return "demoWeb";
  }
  
  @RequestMapping(value = "/loadAiocrProgram", method = RequestMethod.POST)
  @ResponseBody
  public HashMap<String, Object> loadAiocrProgram(
      @RequestParam(value = "requestId") String requestId
      , @RequestParam(value = "callbackUrl") String callbackUrl
      , @RequestParam(value = "format", required = false) String format
      , @RequestParam(value = "ocrFiles") MultipartFile[] ocrFiles
      , HttpServletRequest request) throws Exception {
    
    Logger.info("##### loadAiocrProgram START ##### \t requestId : " + requestId);
    HashMap<String, Object> result = new HashMap<String, Object>();
    
    try {
      // 1. Sync, Async 정책에 따른 API 제공 처리
      if(!asyncYn) throw new Exception("호출정보를 확인해주세요.");
      
      // 2. RequestID 중복 여부 체크
      aiocrMainSvc.checkRequestId(requestId);

      // 3. 요청받은 파일 처리
      aiocrMainSvc.setOcrProcess(requestId, callbackUrl, format, ocrFiles, request);
      
      result.put("rsp_code", HttpStatus.OK);
      result.put("rsp_msg", "success");
    } catch(Exception error) {
      Logger.error("##### loadAiocrProgram error : " + error.getMessage());
      result.put("rsp_code", HttpStatus.BAD_REQUEST);
      result.put("rsp_msg", error.getMessage());
    }
    
    Logger.info("##### loadAiocrProgram END #####");
    return result;
  }
  
  @RequestMapping(value = "/getOcrResult", method = RequestMethod.POST)
  @ResponseBody
  public void getOcrResult(
      @RequestBody(required = false) JSONObject reqBody
      , HttpServletRequest request) throws Exception {
    
    // 트윈리더 분석/추출 결과 PARAM
    String requestId = (String) reqBody.get("requestId");
    
    Logger.info("##### getOcrResult START ##### \t requestId : " + requestId);
    HashMap<String, Object> result  = new HashMap<String, Object>();
    
    try {
      // 1. 분석/추출 결과 수정 및 PARAM 추가
      Logger.info("##### reqBody : " + reqBody);
      JSONArray ocrResult = aiocrMainSvc.getOcrResult(requestId, reqBody, request);
      
      result.put("rsp_code", HttpStatus.OK);
      result.put("rsp_msg", "success");
      result.put("result", ocrResult);
    } catch(Exception error) {
      Logger.error("##### getOcrResult error : " + error.getMessage());
      result.put("rsp_code", HttpStatus.BAD_REQUEST);
      result.put("rsp_msg", error.getMessage());
    }
    
    // 2. 항목 추출 최종 결과 고객사 전달
    aiocrMainSvc.callCallbackUrl(requestId, result, request);
    
    // 3. 서버에 저장 된 파일 삭제 (INPUT, OUTPUT)
    if("true".equals(deleteYn)) {
      File inputFolder = new File("/data/twinreader/data/input/" + requestId + "/");
      if(inputFolder.exists()) {
        FileUtils.cleanDirectory(inputFolder);
        inputFolder.delete();
      }
      
      File outputFolder = new File("/data/twinreader/data/output/" + requestId + "/");
      if(outputFolder.exists()) {
        FileUtils.cleanDirectory(outputFolder);
        outputFolder.delete();
      }
    }
    
    Logger.info("##### getOcrResult END #####");
  }
  
  @RequestMapping(value="/callbackTest", method = RequestMethod.POST)
  @ResponseBody
  public HashMap<String, Object> callbackTest(@RequestBody HashMap<String, Object> request) {
    
    Logger.info("##### callbackTest START #####");
    HashMap<String, Object> result = new HashMap<String, Object>();
    
    result.put("rsp_code", HttpStatus.OK);
    result.put("rsp_msg", "SUCCESS");
    result.put("request", request);
    
    Logger.info("##### callbackTest END #####");
    
    return result;
  }
  
}
