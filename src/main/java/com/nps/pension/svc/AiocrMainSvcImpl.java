package com.nps.pension.svc;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

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

@Service("aiocrMainSvc")
public class AiocrMainSvcImpl implements  AiocrMainSvc{
  
  private static final Logger Logger = LoggerFactory.getLogger(AiocrMainSvcImpl.class);
  
  @Autowired
  SqlSessionTemplate sqlSessionTemplate;
  
  /**
   * 요청받은 파일 적재 후 분석 요청
   * @param requestId
   * @param callbackUrl
   * @param ocrFiles
   * @param request
   * @throws Exception
   */
  public void setOcrProcess(String requestId, String callbackUrl, MultipartFile[] ocrFiles, HttpServletRequest request) throws Exception {
    
    // 현재 시간
    LocalDateTime now = LocalDateTime.now();
    String formatNow  = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    
    // 현재 서버 IP, WebClient 객체 생성
    String serverIp = request.getRemoteAddr();
    WebClient webClient = setWebClient(request, "http://"+serverIp+":8080");
    
    // 1. DB NPSPEN0001, NPSPEN0002 PARAM SET
    Logger.info("1. DB NPSPEN0001, NPSPEN0002 PARAM SET");
    NpsPenHistoryDTO npsPenHistoryDTO = new NpsPenHistoryDTO();
    npsPenHistoryDTO.setRequestId(requestId);
    npsPenHistoryDTO.setReqDt(formatNow);
    npsPenHistoryDTO.setCallbackUrl(callbackUrl);
    npsPenHistoryDTO.setResDt(formatNow);
    npsPenHistoryDTO.setRegDt(formatNow);
    
    npsPenHistoryDTO.setPageNum(0);        // 항목 추출 후 UPDATE
    npsPenHistoryDTO.setCategory(null);    // 항목 추출 후 UPDATE
    npsPenHistoryDTO.setProStatus(null);   // 항목 추출 후 UPDATE
    
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
      JSONObject delApiResult = webClient.method(HttpMethod.DELETE)
          .uri("/twinreader-mgr-service/api/v1/analysis/deleteImageData")
          .bodyValue(jsonObject)
          .retrieve()
          .bodyToMono(JSONObject.class)
          .block();
    } catch(Exception error) {
      Logger.error("##### AIOCR RequestID DB DELETE FAILED " + error.getMessage());
      throw new Exception("AIOCR RequestID DB DELETE FAILED");
    }
    
    // 7. 트윈리더 이미지 분석 요청 API 호출 - /twinreader-mgr-service/api/v1/analysis/inference/reqId
    Logger.info("7. 트윈리더 이미지 분석 요청");
    try {
      JSONObject analysisObj = new JSONObject();
      JSONArray analysisArr = new JSONArray();
      analysisArr.add("/"+requestId+"/");
      analysisObj.put("images", analysisArr);
      analysisObj.put("requestId", requestId);
      analysisObj.put("callbackUrl", "http://"+serverIp+":9100/api/v1/aiocr/getOcrResult");
      JSONObject loadAnalysis = webClient.post()
          .uri("/twinreader-mgr-service/api/v1/analysis/inference/reqId")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(analysisObj)
          .retrieve()
          .bodyToMono(JSONObject.class)
          .block();
    } catch(Exception error) {
      Logger.error("##### AIOCR Analysis FAILED " + error.getMessage());
      throw new Exception("AIOCR Analysis FAILED");
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
  public JSONArray getOcrResult(String requestId, JSONObject reqBody, HttpServletRequest request) throws Exception {
    
    JSONArray ocrResult = new JSONArray();
    
    // 현재 시간
    LocalDateTime now = LocalDateTime.now();
    String formatNow  = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    
    // 현재 서버 IP, WebClient 객체 생성
    String serverIp = request.getRemoteAddr();
    WebClient webClient = setWebClient(request, "http://"+serverIp+":8080");
    
    // 1. DB NPSPEN0001, NPSPEN0002 PARAM SET
    Logger.info("1. DB NPSPEN0001, NPSPEN0002 PARAM SET");
    NpsPen0002DTO npsPen0002DTO = new NpsPen0002DTO();
    npsPen0002DTO.setRequestId(requestId);
    
    ArrayList successPathList = (ArrayList) reqBody.get("successPathList");
    for(int i=0; i<successPathList.size(); i++) {
      JSONObject ocrObj   = new JSONObject();
      
      String imagePath    = (String) successPathList.get(i);
      String imageName    = imagePath.replace("/" + requestId + "/", "");
      String tmpPath      = imagePath.substring(0, imagePath.lastIndexOf(".")) + "_" + imagePath.substring(imagePath.lastIndexOf(".")+1);
      
      String filePath     = "/data/twinreader/data/output" + tmpPath + tmpPath.replace(requestId, "extractionResult") + "_extract_result.json";
      
      // 2. DB NPSPEN0001, NPSPEN0002 PARAM SET
      Logger.info("2. DB NPSPEN0001, NPSPEN0002 PARAM SET");
      npsPen0002DTO.setFileNm(imageName);
      
      // 3. 항목 추출 결과 OUTPUT 경로에서 가져오기
      Logger.info("3. 항목 추출 결과 OUTPUT 경로에서 가져오기");
      File readFile       = new File(filePath);
      if(readFile.getParentFile().exists()) {
        BufferedReader br   = new BufferedReader((new InputStreamReader(new FileInputStream(readFile))));
        String strJson      = br.readLine();
        JSONParser par      = new JSONParser();
        JSONObject jsonObj  = (JSONObject) par.parse(strJson);
        
        JSONObject tmpObj = new JSONObject();
        
        Logger.info("##### jsonObj : " + jsonObj);
        Iterator pages = jsonObj.keySet().iterator();
        while(pages.hasNext()) {
          String pageNum = pages.next().toString();
          Logger.info("##### pageNum : " + pageNum);
          
          JSONObject pageObj  = (JSONObject) jsonObj.get(pageNum);
          JSONObject metaData  = (JSONObject) pageObj.get("metaData");
          
          // 4. DB NPSPEN0002 UPDATE or INSERT
          Logger.info("4. DB NPSPEN0002 UPDATE or INSERT");
          int pageNumber = Integer.parseInt(pageNum.replace("Page", ""));
          npsPen0002DTO.setPageNum(pageNumber);
          npsPen0002DTO.setCategory((String) metaData.get("classification"));
          npsPen0002DTO.setProStatus("success");
          
          if(pageNumber > 1) {
            Logger.info("##### NpsPen0002Sql.insertFileAdd 호출");
            Logger.info("##### npsPen0002DTO 값 : " + npsPen0002DTO.toString());
            sqlSessionTemplate.insert("NpsPen0002Sql.insertFileAdd", npsPen0002DTO);
          } else {
            Logger.info("##### NpsPen0002Sql.updateNpsPen0002 호출");
            Logger.info("##### npsPen0002DTO 값 : " + npsPen0002DTO.toString());
            sqlSessionTemplate.update("NpsPen0002Sql.updateNpsPen0002", npsPen0002DTO);
          }
          
          // 5. 항목 추출 결과 구조 변경 (불필요 데이터 삭제)
          Logger.info("5. 항목 추출 결과 구조 변경 (불필요 데이터 삭제)");
          pageObj.remove("metaData");
          pageObj.remove("version");
          pageObj.remove("requestMetaData");
          
          tmpObj.put(pageNum, pageObj);
        }
        
        ocrObj.put("fileNm", imageName);
        ocrObj.put("fileResult", tmpObj);
        ocrResult.add(ocrObj);
      } else {
        // 6. DB NPSPEN0002 UPDATE
        Logger.info("6. DB NPSPEN0002 UPDATE");
        npsPen0002DTO.setPageNum(0);
        npsPen0002DTO.setCategory("분류실패");
        npsPen0002DTO.setProStatus("failed");
        sqlSessionTemplate.update("NpsPen0002Sql.updateNpsPen0002", npsPen0002DTO);
        
        ocrObj.put("fileNm", imageName);
        ocrObj.put("fileResult", new JSONObject());
        ocrResult.add(ocrObj);
      }
    }
    
    // 7. 실패 건에 대해 처리
    Logger.info("7. 실패 건에 대해 처리");
    ArrayList failInfoList = (ArrayList) reqBody.get("failInfoList");
    for(int i=0; i<failInfoList.size(); i++) {
      JSONObject ocrObj   = new JSONObject();
      
      String imagePath    = (String) successPathList.get(i);
      String imageName    = imagePath.replace("/" + requestId + "/", "");
      
      // 8. DB NPSPEN0002 UPDATE
      Logger.info("8. DB NPSPEN0002 UPDATE");
      npsPen0002DTO.setFileNm(imageName);
      npsPen0002DTO.setPageNum(0);
      npsPen0002DTO.setCategory("분류실패");
      npsPen0002DTO.setProStatus("failed");
      sqlSessionTemplate.update("NpsPen0002Sql.updateNpsPen0002", npsPen0002DTO);
      
      ocrObj.put("fileNm", imageName);
      ocrObj.put("fileResult", new JSONObject());
      ocrResult.add(ocrObj);
    }
    
    return ocrResult;
  }
  
  
  /**
   * 고객사 CallBackUrl 호출
   * @param requestId
   * @param result
   * @param request
   * @throws Exception
   */
  public void callCallbackUrl(String requestId, HashMap<String, Object> result, HttpServletRequest request) throws Exception {
    
    // 현재 시간
    LocalDateTime now = LocalDateTime.now();
    String formatNow  = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    
    // 현재 서버 IP
    String serverIp = request.getRemoteAddr();
    
    // 1. 요청에 대한 DB 값 가져오기 (CALLBACK_URL)
    Logger.info("1. 요청에 대한 DB 값 가져오기 (CALLBACK_URL)");
    HashMap<String, Object> selectParam = new HashMap<>();
    selectParam.put("requestId", requestId);
    String callbackUrl = sqlSessionTemplate.selectOne("NpsPen0001Sql.selectCallbackUrl", selectParam);
    Logger.info("##### selectAipct0001 : " + callbackUrl);
    
    // 2. CallbackUrl 호출, WebClient 객체 생성
    WebClient webClient = setWebClient(request, callbackUrl);
    Logger.info("2. CallbackUrl 호출");
    JSONObject callbackResult = webClient.method(HttpMethod.POST)
        .bodyValue(new JSONObject(result))
        .retrieve()
        .bodyToMono(JSONObject.class)
        .block();
    
    // 3. DB NPSPEN0001 UPDATE
    Logger.info("3. DB NPSPEN0001 UPDATE");
    NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
    npsPen0001DTO.setRequestId(requestId);
    npsPen0001DTO.setResDt(formatNow);
    sqlSessionTemplate.update("NpsPen0001Sql.updateNpsPen0001", npsPen0001DTO);
    
    Logger.info("##### CallbackUrl 호출 결과 : " + callbackResult);
    
  }
  
  /**
   * WebClient 생성
   * @param request
   * @return WebClient
   */
  public WebClient setWebClient(HttpServletRequest request, String baseUrl) {
    
    WebClient webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
    
    return webClient;
  }
  
}
