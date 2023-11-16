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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.HashMap;

@Controller
@RequestMapping("/api/v1/aiocr")
@CrossOrigin(origins = "*")
public class AiocrMainCtl {
  
  private static final Logger Logger = LoggerFactory.getLogger(AiocrMainCtl.class);
  
  @Resource(name = "aiocrMainSvc")
  private AiocrMainSvc aiocrMainSvc;
  
  @Value("${luna.test}")
  String testValue;
  
  @RequestMapping(value="/test", method = RequestMethod.GET)
  @ResponseBody
  public HashMap<String, Object> test() {
    HashMap<String, Object> result = new HashMap<String, Object>();
    
    result.put("rsp_code", HttpStatus.OK);
    result.put("rsp_msg", "success");
    
    result.put("testValue", testValue);
    
    return result;
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
    /*
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
    */
    
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
