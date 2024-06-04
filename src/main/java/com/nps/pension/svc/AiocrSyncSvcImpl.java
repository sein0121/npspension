package com.nps.pension.svc;

import com.nps.pension.config.WebClientUtil;
import com.nps.pension.dto.NpsPen0001DTO;
import com.nps.pension.dto.NpsPen0002DTO;
import com.nps.pension.dto.NpsPenHistoryDTO;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

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

  @Value("${twinreader.port}")
  String twinPort;
  
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
  public void checkRequestId(String requestId, String type) throws Exception {
    
    // 1. DB NPSPEN0001 PARAM SET
    Logger.info("1. DB NPSPEN0001 PARAM SET");
    NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
    npsPen0001DTO.setRequestId(requestId);
    
    // 2. NPSPEN0001 테이블 RequestID 개수 조회
    Logger.info("2. NPSPEN0001 테이블 RequestID 개수 조회");
    int reqIdCnt = sqlSessionTemplate.selectOne("NpsPen0001Sql.selectReqIdCnt", npsPen0001DTO);
    
    // 3. 이 전에 사용 된 RequestID 인 경우 오류 처리
    if(reqIdCnt > 0 && "dupl".equals(type)) throw new Exception("중복되는 Request ID 입니다.");
    else if(reqIdCnt == 0 && "exis".equals(type)) throw new Exception("존재하지 않는 Request ID 입니다.");
    
  }

  /**
   * 요청받은 파일 적재 후 분석 요청
   * @param requestId
   * @param filename
   * @param request
   * @throws Exception
   */
  public void setOcrProcess(String requestId, String format, HttpServletRequest request) throws Exception {

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
    
    npsPenHistoryDTO.setPageNum(0);           // 항목 추출 후 UPDATE
    npsPenHistoryDTO.setCategory(null);       // 항목 추출 후 UPDATE
    npsPenHistoryDTO.setProStatus("start");   // 항목 추출 후 UPDATE
    npsPenHistoryDTO.setProMsg(null);         // 항목 추출 후 UPDATE
    
    // 2. 요청에 대한 DB NPSPEN0001 INSERT
    Logger.info("2. 요청에 대한 DB NPSPEN0001 INSERT");
    sqlSessionTemplate.insert("NpsPen0001Sql.insertNpsPen0001", npsPenHistoryDTO);

    // 3. 전달받은 파일 INPUT 경로에 저장
    Logger.info("3. 전달받은 파일 INPUT 경로에 저장");
    String inputPath = "/data/twinreader/data/input/" + requestId + "/";
//    for (MultipartFile ocrFile : filename) {
//      String fileName1 = ocrFile.getOriginalFilename();
//      File saveDir = new File(inputPath + fileName1);
//
//      // 4. 디렉토리가 없는 경우 디렉토리 생성
//      Logger.info("4. 디렉토리가 없는 경우 디렉토리 생성");
//      if(!saveDir.getParentFile().exists()) saveDir.getParentFile().mkdirs();
//      ocrFile.transferTo(saveDir);
//
//      // 5. 요청에 대한 DB NPSPEN0002 INSERT
//      Logger.info("5. 요청에 대한 DB NPSPEN0002 INSERT");
//      npsPenHistoryDTO.setFileNm(fileName1);
//      sqlSessionTemplate.insert("NpsPen0002Sql.insertNpsPen0002", npsPenHistoryDTO);
//    }
    MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
    Iterator<String> fileNames = multipartRequest.getFileNames();
    while(fileNames.hasNext()) {

      String fileName = fileNames.next();
      MultipartFile mFile = multipartRequest.getFile(fileName);

      File saveDir = new File(inputPath + fileName);

      if (mFile.getSize() != 0) // FIle null Check
      {
        // 4. 디렉토리가 없는 경우 디렉토리 생성
        Logger.info("4. 디렉토리가 없는 경우 디렉토리 생성");
        if(!saveDir.getParentFile().exists()) saveDir.getParentFile().mkdirs();
        mFile.transferTo(saveDir);

        // 5. 요청에 대한 DB NPSPEN0002 INSERT
        Logger.info("5. 요청에 대한 DB NPSPEN0002 INSERT");
        npsPenHistoryDTO.setFileNm(fileName);
        sqlSessionTemplate.insert("NpsPen0002Sql.insertNpsPen0002", npsPenHistoryDTO);

      }
    }
    
    // 6. 트윈리더 DB정보 삭제 API 호출 - /twinreader-mgr-service/api/v1/analysis/deleteImageData
    Logger.info("6. 트윈리더 DB정보 삭제 API 호출");
    try {
      JSONObject jsonObject = new JSONObject();
      JSONArray jsonArray = new JSONArray();
      jsonArray.add("/"+requestId+"/");
      jsonObject.put("images", jsonArray);
      JSONObject delApiResult = webClientUtil.delete(
          "https://" +serverIP+twinPort+"/twinreader-mgr-service/api/v1/analysis/deleteImageData"
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
        analysisObj.put("callbackUrl", "https://"+serverIP+":"+port+"/api/v1/aiocr/setProStatus");
        JSONObject loadAnalysis = webClientUtil.post(
            "https://"+serverIP+twinPort+"/twinreader-mgr-service/api/v1/analysis/inference/reqId"
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
        analysisObj.put("callbackUrl", "https://"+serverIP+":"+port+"/api/v1/aiocr/setProStatus");
        analysisObj.put("pipelineName", pipelineName);
        analysisObj.put("clsfGroupID", clsfGroupID);
        
        JSONObject loadAnalysis = webClientUtil.post(
            "https://"+serverIP+twinPort+"/twinreader-mgr-service/api/v2/flow/twrd"
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
    
    // 1. DB NPSPEN0001, NPSPEN0002 PARAM SET
    Logger.info("1. DB NPSPEN0001, NPSPEN0002 PARAM SET");
    NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
    npsPen0001DTO.setRequestId(requestId);
    npsPen0001DTO.setProStatus("analysis");
    npsPen0001DTO.setResDt(formatNow);
    
    NpsPen0002DTO npsPen0002DTO = new NpsPen0002DTO();
    npsPen0002DTO.setRequestId(requestId);
    
    try {
      // 2. DB NPSPEN0001 PRO_STATUS UPDATE
      Logger.info("2. DB NPSPEN0001 PRO_STATUS UPDATE");
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
  public void getProStatus(String requestId) throws Exception {
    
    // 1. DB NPSPEN0001 PARAM SET
    NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
    npsPen0001DTO.setRequestId(requestId);
    
    int idx           = 1;
    String proStatus  = "";
    
    // 2. DB NPSPEN0001 PRO_STATUS 조회 (10초에 한 번, 2분 동안 조회)
    while(!"analysis".equals(proStatus) && idx <= threadTimeout) {
      Logger.info("2. DB NPSPEN0001 PRO_STATUS 조회 " + idx);
      Thread.sleep(threadSleep);
      proStatus = sqlSessionTemplate.selectOne("NpsPen0001Sql.selectProStatus", npsPen0001DTO);
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
    NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
    npsPen0001DTO.setRequestId(requestId);
    npsPen0001DTO.setProStatus("finish");
    npsPen0001DTO.setResDt(formatNow);
    
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
          "https://"+serverIP+twinPort+"/twinreader-mgr-service/api/v1/analysis/category"
          , jsonObject
          , JSONArray.class
      );
    } catch(Exception error) {
      Logger.error("##### Twinreader ANALYSIS RESULT SEARCH FAILED " + error.getMessage());
      throw new Exception("Twinreader ANALYSIS RESULT SEARCH FAILED");
    }
    
    // 3. 고객사 요청에 대한 DB 값 가져오기 (FORMAT)
    Logger.info("3. 고객사 요청에 대한 DB 값 가져오기 (FORMAT)");
    HashMap<String, Object> selectReqInfo = sqlSessionTemplate.selectOne("NpsPen0001Sql.selectReqInfo", npsPen0001DTO);
    String format         = (String) selectReqInfo.get("FORMAT");
    
    for(int i=0; i<analyArr.size(); i++) {
      HashMap<String, Object> analyObj = (HashMap<String, Object>) analyArr.get(i);
      JSONObject ocrObj   = new JSONObject();
      
      Boolean success     = (Boolean) analyObj.get("success");
      int pageNumber      = Integer.parseInt((String) analyObj.get("pageNumber"));
      
      String imagePath    = (String) analyObj.get("path");
      String imageName    = imagePath.replace("/" + requestId + "/", "");
      String tmpPath      = imagePath.substring(0, imagePath.lastIndexOf(".")) + "_" + imagePath.substring(imagePath.lastIndexOf(".")+1).toLowerCase();
//      Logger.info("\n1️⃣1️⃣"+imagePath+"\n2️⃣2️⃣2️⃣" +imageName+"\n3️⃣3️⃣3️⃣3️⃣"+tmpPath);
      
      String filePath     = "/data/twinreader/data/output" + tmpPath + tmpPath.replace(requestId, "extractionResult") + "_extract_result.json";
      
      // 4. DB NPSPEN0002 PARAM SET
      Logger.info("4. DB NPSPEN0002 PARAM SET");
      npsPen0002DTO.setFileNm(imageName);
      npsPen0002DTO.setPageNum(pageNumber);
      npsPen0002DTO.setCategory((String) analyObj.get("category"));
      npsPen0002DTO.setProStatus(success ? "success" : "failed");
      npsPen0002DTO.setProMsg((String) analyObj.get("message"));
      
      // 5. 항목 추출 결과 OUTPUT 경로에서 가져오기
      // 5-1. 분석 성공한 경우
      if(success) {
        Logger.info("5-1. 분석 성공한 경우");
        try {
          // 멀티페이지인 경우 1개의 JSON 파일에 결과가 나오기 때문에, 중복 처리를 방지하기 위해 조건문 추가
          if(pageNumber < 2) {
            // 6. 항목 추출 결과 OUTPUT 경로에서 가져오기
            Logger.info("6. 항목 추출 결과 OUTPUT 경로에서 가져오기");
            File readFile         = new File(filePath);
            if(readFile.getParentFile().exists()) {
              BufferedReader br   = new BufferedReader((new InputStreamReader(new FileInputStream(readFile))));
              String strJson      = br.readLine();
              JSONParser par      = new JSONParser();
              JSONObject jsonObj  = (JSONObject) par.parse(strJson);
              
              JSONObject tmpObj   = new JSONObject();
              
              Iterator pages = jsonObj.keySet().iterator();
              while(pages.hasNext()) {
                String pageNum = pages.next().toString();
                JSONObject pageObj  = (JSONObject) jsonObj.get(pageNum);
                JSONObject metaData  = (JSONObject) pageObj.get("metaData");
                
                // 5. DB NPSPEN0002 UPDATE or INSERT
                Logger.info("5. DB NPSPEN0002 UPDATE or INSERT");
                pageNumber = Integer.parseInt(pageNum.replace("Page", ""));
                npsPen0002DTO.setPageNum(pageNumber);
                npsPen0002DTO.setCategory((String) metaData.get("classification"));
                
                if(pageNumber > 1) {
                  Logger.info("##### NpsPen0002Sql.insertNpsPen0002 호출");
                  Logger.info("##### npsPen0002DTO 값 : " + npsPen0002DTO);
                  sqlSessionTemplate.insert("NpsPen0002Sql.insertNpsPen0002", npsPen0002DTO);
                } else {
                  Logger.info("##### NpsPen0002Sql.updateNpsPen0002 호출");
                  Logger.info("##### npsPen0002DTO 값 : " + npsPen0002DTO);
                  sqlSessionTemplate.update("NpsPen0002Sql.updateNpsPen0002", npsPen0002DTO);
                }
                
                // 6. 항목 추출 결과 구조 변경 (불필요 데이터 삭제)
                Logger.info("6. 항목 추출 결과 구조 변경 (불필요 데이터 삭제)");
                pageObj.remove("metaData");
                pageObj.remove("version");
                pageObj.remove("requestMetaData");
                if("simple".equals(format)) pageObj.remove("values");
                
                // 7. 페이지 별 카테고리 정보 추가
                Logger.info("7. 페이지 별 카테고리 정보 추가");
                pageObj.put("category", metaData.get("classification"));
                
                tmpObj.put(pageNum, pageObj);
              }
              
              ocrObj.put("fileNm", imageName);
              ocrObj.put("fileResult", tmpObj);
              ocrResult.add(ocrObj);
            } else {
              // 8. DB NPSPEN0002 UPDATE
              Logger.info("8. DB NPSPEN0002 UPDATE");
              npsPen0002DTO.setPageNum(0);
              npsPen0002DTO.setCategory("분류실패");
              npsPen0002DTO.setProStatus("failed");
              npsPen0002DTO.setProMsg(null);
              sqlSessionTemplate.update("NpsPen0002Sql.updateNpsPen0002", npsPen0002DTO);
              
              ocrObj.put("fileNm", imageName);
              ocrObj.put("fileResult", new JSONObject());
              ocrResult.add(ocrObj);
            }
          }
        } catch(Exception error) {
          Logger.error("##### SUCCESS RESULT PROCESS FAILED " + error.getMessage());
          throw new Exception("SUCCESS RESULT PROCESS FAILED");
        }
      }
      // 5-2. 분석 실패한 경우
      else {
        Logger.info("5-2. 분석 실패한 경우");
        try {
          sqlSessionTemplate.update("NpsPen0002Sql.updateNpsPen0002", npsPen0002DTO);
          
          ocrObj.put("fileNm", imageName);
          ocrObj.put("fileResult", new JSONObject());
          ocrResult.add(ocrObj);
        } catch(Exception error) {
          Logger.error("##### FAILED RESULT PROCESS FAILED " + error.getMessage());
          throw new Exception("FAILED RESULT PROCESS FAILED");
        }
      }
    }
    
    return ocrResult;
  }
  
  public void deleteDirectory(String requestId) throws Exception {
    
    Logger.info("##### deleteDirectory START #####" + requestId);
    
    try {
      File inputFolder = new File("/data/twinreader/data/input/" + requestId + "/");
      if (inputFolder.exists()) {
        FileUtils.cleanDirectory(inputFolder);
        inputFolder.delete();
      }
      
      File outputFolder = new File("/data/twinreader/data/output/" + requestId + "/");
      if (outputFolder.exists()) {
        FileUtils.cleanDirectory(outputFolder);
        outputFolder.delete();
      }
    } catch(Exception error) {
      Logger.error("##### INPUT, OUTPUT DIRECTORY FAILED : " + error.getMessage());
    }
    
    // TEST
//    Thread.sleep(10000);
    
    Logger.info("##### deleteDirectory END #####");
  }
  
}
