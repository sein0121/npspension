package com.nps.pension.ctl;

import com.nps.pension.svc.AiocrSyncSvc;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.HashMap;

@Controller
@RequestMapping("/api/v1/aiocr")
@CrossOrigin(origins = "*")
public class AiocrSyncCtl {
  
  private static final org.slf4j.Logger Logger = LoggerFactory.getLogger(AiocrSyncCtl.class);
  
  @Resource(name = "aiocrSyncSvc")
  private AiocrSyncSvc aiocrSyncSvc;
  
  @Value("${twinreader.output.deleteYn}")
  String deleteYn;
  
  @Value("${aipct.pension.sync}")
  Boolean syncYn;

//  @RequestMapping(value = "/aiocrSyncLoad", method = RequestMethod.POST)
  @PostMapping(value = "/aiocrSyncLoad")
  @ResponseBody
  public HashMap<String, Object> aiocrSyncLoad(
      @RequestParam(value = "requestId") String requestId
      , @RequestParam(value = "format") String format
//      , @RequestParam(value = "filename") MultipartFile[] filename
      , HttpServletRequest request) throws Exception {


    Logger.info("##### aiocrSyncLoad START ##### \t requestId : " + requestId);
    HashMap<String, Object> result = new HashMap<String, Object>();
    
    try {
      // 1. Sync, Async 정책에 따른 API 제공 처리
      if(!syncYn) throw new Exception("호출정보를 확인해주세요.");
      
      // 2. RequestID 중복 여부 체크
      aiocrSyncSvc.checkRequestId(requestId, "dupl");
      
      // 3. 파일 INPUT 경로에 추가 후 분석 요청
      aiocrSyncSvc.setOcrProcess(requestId, format, request);
//      aiocrSyncSvc.setOcrProcess(requestId, format, filename, request);

      // 4. 트윈리더 처리 완료 여부 조회 (PRO_STATUS)
      aiocrSyncSvc.getProStatus(requestId);
      
      // 5. 항목 추출 결과 OUTPUT 경로에서 가져와 수정
      JSONArray ocrResult = aiocrSyncSvc.getOcrResult(requestId, request);
      
      result.put("resultCode", HttpStatus.OK);
      result.put("resultMessage", "success");
      result.put("result", ocrResult);
    } catch(Exception error) {
      Logger.error("##### aiocrSyncLoad error : " + error.getMessage());
      result.put("resultCode", HttpStatus.BAD_REQUEST);
      result.put("resultMessage", error.getMessage());
    }
    
    // 5. 서버에 저장 된 파일 삭제 (INPUT, OUTPUT)
    try {
      if ("true".equals(deleteYn)) {
        aiocrSyncSvc.deleteDirectory(requestId);
      }
    } catch(Exception error) {
      Logger.error("##### INPUT, OUTPUT DIRECTORY FAILED : " + error.getMessage());
    }
    
    Logger.info("##### aiocrSyncLoad END #####" + result);
    return result;
  }
  
  @RequestMapping(value = "/setProStatus", method = RequestMethod.POST)
  @ResponseBody
  public void setProStatus(
      @RequestBody(required = false) JSONObject reqBody
      , HttpServletRequest request) throws Exception {
    
    // 트윈리더 분석/추출 결과 PARAM
    String requestId = (String) reqBody.get("requestId");
    
    Logger.info("##### setProStatus START ##### \t requestId : " + requestId);
    
    try {
      // 1. DB NPSPEN0001 PRO_STATUS UPDATE (analysis)
      Logger.info("##### reqBody : " + reqBody);
      aiocrSyncSvc.setProStatus(requestId, reqBody, request);
    } catch(Exception error) {
      Logger.error("##### setProStatus error : " + error.getMessage());
    }
    
    Logger.info("##### setProStatus END #####");
  }
  
  @RequestMapping(value="/aiocrResultLoad", method = RequestMethod.POST)
  @ResponseBody
  public HashMap<String, Object> aiocrResultLoad(
      @RequestParam(value = "requestId") String requestId
      , HttpServletRequest request) throws Exception {
    
    Logger.info("##### aiocrResultLoad START ##### \t requestId : " + requestId);
    
    HashMap<String, Object> result = new HashMap<String, Object>();
    
    try {
      // 1. RequestID 유무 체크
      aiocrSyncSvc.checkRequestId(requestId, "exis");
      
      // 2. 항목 추출 결과 OUTPUT 경로에서 가져와 수정
      JSONArray ocrResult = aiocrSyncSvc.getOcrResult(requestId, request);
      
      result.put("resultMCode", HttpStatus.OK);
      result.put("resultMessage", "success");
      result.put("result", ocrResult);
    } catch(Exception error) {
      Logger.error("##### aiocrSyncLoad error : " + error.getMessage());
      result.put("resultMCode", HttpStatus.BAD_REQUEST);
      result.put("resultMessage", error.getMessage());
    }
    
    // 3. 서버에 저장 된 파일 삭제 (INPUT, OUTPUT)
    try {
      if ("true".equals(deleteYn)) {
        aiocrSyncSvc.deleteDirectory(requestId);
      }
    } catch(Exception error) {
      Logger.error("##### INPUT, OUTPUT DIRECTORY FAILED : " + error.getMessage());
    }
    
    Logger.info("##### aiocrResultLoad END #####" + result);
    return result;
  }
  
}