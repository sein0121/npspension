package com.nps.pension.svc;

import com.nps.pension.config.WebClientUtil;
import com.nps.pension.dto.NpsPen0001DTO;
import com.nps.pension.dto.NpsPen0002DTO;
import com.nps.pension.dto.NpsPenHistoryDTO;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

@Service("aiocrSyncSvc")
public class AiocrSyncSvcImpl implements AiocrSyncSvc {
  
  private static final Logger Logger = LoggerFactory.getLogger(AiocrSyncSvcImpl.class);
  
  @Autowired
  SqlSessionTemplate sqlSessionTemplate;
  
  @Autowired
  WebClientUtil webClientUtil;
  
  @Value("${server.ip}")
  String serverIP;
  
  @Value("${aipct.pension.port}")
  String port;
  
  @Value("${aipct.pension.thread.timeout}")
  int threadTimeout;
  
  @Value("${aipct.pension.thread.sleep}")
  int threadSleep;
  
  @Value("${twinreader.version}")
  String twinreaderVersion;
  
  @Value("${twinreader.analysis.pipelineName}")
  String pipelineName;
  
  @Value("${twinreader.analysis.clsfGroupID}")
  String clsfGroupID;
  
  /**
   * 요청받은 RequestID 중복여부 체크
   * @param requestId
   * @throws Exception
   */
  public void checkRequestId(String requestId) throws Exception {
    
    // 1. DB NPSPEN0001 PARAM SET
    Logger.info("1. DB NPSPEN0001 PARAM SET");
    NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
    npsPen0001DTO.setRequestId(requestId);
    
    // 2. NPSPEN0001 테이블 RequestID 개수 조회
    int reqIdCnt = sqlSessionTemplate.selectOne("NpsPen0001Sql.selectReqIdCnt", npsPen0001DTO);
    
    // 3. 이 전에 사용 된 RequestID 인 경우 오류 처리
    if(reqIdCnt > 0) throw new Exception("중복되는 Request ID 입니다.");
  }
  
  /**
   * 요청받은 파일 적재 후 분석 요청
   * @param requestId
   * @param ocrFiles
   * @param request
   * @throws Exception
   */
  public void setOcrProcess(String requestId, String format, MultipartFile[] ocrFiles, HttpServletRequest request) throws Exception {
    
    // 현재 시간
    LocalDateTime now = LocalDateTime.now();
    String formatNow  = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    
    // 1. DB NPSPEN0001, NPSPEN0002 PARAM SET
    Logger.info("1. DB NPSPEN0001, NPSPEN0002 PARAM SET");
    NpsPenHistoryDTO npsPenHistoryDTO = new NpsPenHistoryDTO();
    npsPenHistoryDTO.setRequestId(requestId);
    npsPenHistoryDTO.setFormat(format);
    npsPenHistoryDTO.setReqDt(formatNow);
    npsPenHistoryDTO.setResDt(formatNow);
    npsPenHistoryDTO.setRegDt(formatNow);
    
    // Sync 방식의 경우 callbackUrl 불필요
    npsPenHistoryDTO.setCallbackUrl(null);
    
    npsPenHistoryDTO.setPageNum(0);        // 항목 추출 후 UPDATE
    npsPenHistoryDTO.setCategory(null);    // 항목 추출 후 UPDATE
    npsPenHistoryDTO.setProStatus(null);   // 항목 추출 후 UPDATE
    npsPenHistoryDTO.setProMsg(null);      // 항목 추출 후 UPDATE
    
    // 2. 요청에 대한 DB NPSPEN0001 INSERT
    Logger.info("2. 요청에 대한 DB NPSPEN0001 INSERT");
    sqlSessionTemplate.insert("NpsPen0001Sql.insertNpsPen0001", npsPenHistoryDTO);
    
    // 3. 전달받은 파일 INPUT 경로에 저장
    Logger.info("3. 전달받은 파일 INPUT 경로에 저장");
    String inputPath = "/data/twinreader/data/input/" + requestId + "/";
    for (MultipartFile ocrFile : ocrFiles) {
      String fileName = ocrFile.getOriginalFilename();
      File saveDir = new File(inputPath + fileName);
      
      // 4. 디렉토리가 없는 경우 디렉토리 생성
      Logger.info("4. 디렉토리가 없는 경우 디렉토리 생성");
      if(!saveDir.getParentFile().exists()) saveDir.getParentFile().mkdirs();
      ocrFile.transferTo(saveDir);
      
      // 5. 요청에 대한 DB NPSPEN0002 INSERT
      Logger.info("5. 요청에 대한 DB NPSPEN0002 INSERT");
      npsPenHistoryDTO.setFileNm(fileName);
      sqlSessionTemplate.insert("NpsPen0002Sql.insertNpsPen0002", npsPenHistoryDTO);
    }
    
    // 6. 트윈리더 DB정보 삭제 API 호출 - /twinreader-mgr-service/api/v1/analysis/deleteImageData
    Logger.info("6. 트윈리더 DB정보 삭제 API 호출");
    try {
      JSONObject jsonObject = new JSONObject();
      JSONArray jsonArray = new JSONArray();
      jsonArray.add("/"+requestId+"/");
      jsonObject.put("images", jsonArray);
      JSONObject delApiResult = webClientUtil.delete(
          "http://"+serverIP+":8080/twinreader-mgr-service/api/v1/analysis/deleteImageData"
          , jsonObject
          , JSONObject.class
      );
      
      if(!(boolean)delApiResult.get("success")) throw new Exception("Twinreader DELETE IMAGE DATA FAILED");
    } catch(Exception error) {
      Logger.error("##### AIOCR RequestID DB DELETE FAILED " + error.getMessage());
      throw new Exception("AIOCR RequestID DB DELETE FAILED");
    }
    
    // 7. 트윈리더 이미지 분석 요청 API 호출
    Logger.info("7. 트윈리더 이미지 분석 요청");
    try {
      JSONObject analysisObj = new JSONObject();
      JSONArray analysisArr = new JSONArray();
      
      // 트윈리더 2.2 버전 - /twinreader-mgr-service/api/v1/analysis/inference/reqId
      if("2.2".equals(twinreaderVersion)) {
        Logger.info("7-1. 트윈리더 2.2버전 처리");
        analysisArr.add("/"+requestId+"/");
        analysisObj.put("images", analysisArr);
        analysisObj.put("requestId", requestId);
        analysisObj.put("callbackUrl", "http://"+serverIP+":"+port+"/api/v1/aiocr/setProStatus");
        JSONObject loadAnalysis = webClientUtil.post(
            "http://"+serverIP+":8080/twinreader-mgr-service/api/v1/analysis/inference/reqId"
            , analysisObj
            , JSONObject.class
        );
        
        if(!(boolean)loadAnalysis.get("success")) throw new Exception("Twinreader ANALYSIS FAILED");
      }
      // 트윈리더 2.3 버전 - /twinreader-mgr-service/api/v2/flow/twrd
      else {
        Logger.info("7-2. 트윈리더 2.3버전 처리");
        analysisArr.add("/"+requestId+"/");
        analysisObj.put("pathList", analysisArr);
        analysisObj.put("requestID", requestId);
        analysisObj.put("callbackUrl", "http://"+serverIP+":"+port+"/api/v1/aiocr/getOcrResult");
        analysisObj.put("pipelineName", pipelineName);
        analysisObj.put("clsfGroupID", clsfGroupID);
        
        JSONObject loadAnalysis = webClientUtil.post(
            "http://"+serverIP+":8080/twinreader-mgr-service/api/v2/flow/twrd"
            , analysisObj
            , JSONObject.class
        );
        
        if(!(boolean)loadAnalysis.get("success")) throw new Exception("Twinreader ANALYSIS FAILED");
      }
    } catch(Exception error) {
      Logger.error("##### AIOCR Analysis PROCESS FAILED " + error.getMessage());
      throw new Exception("AIOCR Analysis PROCESS FAILED");
    }
  }
  
  /**
   * 분석 완료 후 OUTPUT 경로에서 결과 JSON 가져와 불필요한 데이터 제거
   * @param requestId
   * @param reqBody
   * @param request
   * @return JSONArray OUTPUT 결과 JSON
   * @throws Exception
   */
  public void setProStatus(String requestId, JSONObject reqBody, HttpServletRequest request) throws Exception {
    
    // 현재 시간
    LocalDateTime now = LocalDateTime.now();
    String formatNow  = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    
    // 1. DB NPSPEN0001 PARAM SET
    NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
    npsPen0001DTO.setRequestId(requestId);
    npsPen0001DTO.setProStatus("analysis");
    
    try {
      // 2. DB NPSPEN0001 PRO_STATUS UPDATE
      sqlSessionTemplate.update("NpsPen0001Sql.updateProStatus", npsPen0001DTO);
    } catch(Exception error) {
      Logger.error("##### PRO_STATUS UPDATE FAILED " + error.getMessage());
      Logger.error(npsPen0001DTO.toString());
      throw new Exception("PRO_STATUS UPDATE FAILED");
    }
    
  }
  
  /**
   * DB NPSPEN0001 PRO_STATUS 상태 값 변경 여부 조회
   * @param requestId
   * @throws Exception
   */
  @Transactional(timeout = 120)
  public void getProStatus(String requestId) throws Exception {
    
    // 1. DB NPSPEN0001 PARAM SET
    NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
    npsPen0001DTO.setRequestId(requestId);
    
    int idx           = 1;
    String proStatus  = "";
    
    Logger.info("##### threadTimeout : " + threadTimeout);
    Logger.info("##### threadSleep : " + threadSleep);
    
    // 2. DB NPSPEN0001 PRO_STATUS 조회 (10초에 한 번, 2분 동안 조회)
    while(!"analysis".equals(proStatus) && idx <= threadTimeout) {
      proStatus = sqlSessionTemplate.selectOne("NpsPen0001Sql.selectProStatus", npsPen0001DTO);
      
      Logger.info("..." + idx + " : " + proStatus);
      Logger.info("test : " + "success".equals(proStatus));
      
      Thread.sleep(threadSleep);
      idx++;
    }
    
    if(!"analysis".equals(proStatus)) throw new Exception("AIOCR PROCESS TIMEOUT");
  }
  
  /**
   * 문서분류 결과 API 호출 후 분석결과 데이터 가공
   * @param requestId
   * @param request
   * @return JSONArray OUTPUT 결과 JSON
   * @throws Exception
   */
  public JSONArray getOcrResult(String requestId, HttpServletRequest request) throws Exception {
    
    JSONArray ocrResult = new JSONArray();
    
    // 현재 시간
    LocalDateTime now = LocalDateTime.now();
    String formatNow  = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    
    // 1. DB NPSPEN0001, NPSPEN0002 PARAM SET
    Logger.info("1. DB NPSPEN0001, NPSPEN0002 PARAM SET");
    NpsPen0002DTO npsPen0002DTO = new NpsPen0002DTO();
    npsPen0002DTO.setRequestId(requestId);
    npsPen0002DTO.setRegDt(formatNow);
    
    // 2. 문서분류 결과 조회 - /twinreader-mgr-service/api/v1/analysis/category
    Logger.info("2. 문서분류 결과 조회");
    JSONArray analyArr = new JSONArray();
    try {
      JSONObject jsonObject = new JSONObject();
      JSONArray jsonArray = new JSONArray();
      jsonArray.add("/"+requestId+"/");
      jsonObject.put("images", jsonArray);
      analyArr = webClientUtil.post(
          "http://"+serverIP+":8080/twinreader-mgr-service/api/v1/analysis/category"
          , jsonObject
          , JSONArray.class
      );
    } catch(Exception error) {
      Logger.error("##### Twinreader ANALYSIS RESULT SEARCH FAILED " + error.getMessage());
      throw new Exception("Twinreader ANALYSIS RESULT SEARCH FAILED");
    }
    
    // 3. 고객사 요청에 대한 DB 값 가져오기 (FORMAT)
    Logger.info("3. 고객사 요청에 대한 DB 값 가져오기 (FORMAT)");
    NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
    npsPen0001DTO.setRequestId(requestId);
    HashMap<String, Object> selectReqInfo = sqlSessionTemplate.selectOne("NpsPen0001Sql.selectReqInfo", npsPen0001DTO);
    String format         = (String) selectReqInfo.get("FORMAT");
    
    for(int i=0; i<analyArr.size(); i++) {
      JSONObject ocrObj   = new JSONObject();
      
      JSONObject analyObj = (JSONObject) analyArr.get(i);
      
      Boolean success     = (Boolean) analyObj.get("success");
      
      String imagePath    = (String) analyObj.get("path");
      String imageName    = imagePath.replace("/" + requestId + "/", "");
      String tmpPath      = imagePath.substring(0, imagePath.lastIndexOf(".")) + "_" + imagePath.substring(imagePath.lastIndexOf(".")+1);
      
      String filePath     = "/data/twinreader/data/output" + tmpPath + tmpPath.replace(requestId, "extractionResult") + "_extract_result.json";
      
      // 4. DB NPSPEN0001, NPSPEN0002 PARAM SET
      Logger.info("4. DB NPSPEN0001, NPSPEN0002 PARAM SET");
      npsPen0002DTO.setFileNm(imageName);
      npsPen0002DTO.setPageNum((Integer) analyObj.get("pageNumber"));
      npsPen0002DTO.setCategory((String) analyObj.get("category"));
      npsPen0002DTO.setProStatus(success ? "success" : "failed");
      npsPen0002DTO.setProMsg((String) analyObj.get("message"));
      
      // 5. 항목 추출 결과 OUTPUT 경로에서 가져오기
      // 5-1. 분석 성공한 경우
      if(success) {
        Logger.info("5-1. 분석 성공한 경우");
        
      }
      // 5-2. 분석 실패한 경우
      else {
        Logger.info("5-2. 분석 실패한 경우");
        
      }
      
      
      
      
      JSONObject tmpObj = new JSONObject();
      ocrObj.put("fileNm", imageName);
      ocrObj.put("fileResult", tmpObj);
      
      ocrResult.add(ocrObj);
    }
    
    return ocrResult;
  }
  
}
