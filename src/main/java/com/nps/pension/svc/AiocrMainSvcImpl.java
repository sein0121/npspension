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
import java.util.Objects;

@Service("aiocrMainSvc")
public class AiocrMainSvcImpl implements  AiocrMainSvc{
  
  private static final Logger Logger = LoggerFactory.getLogger(AiocrMainSvcImpl.class);
  
  @Autowired
  SqlSessionTemplate sqlSessionTemplate;
  
  @Autowired
  WebClientUtil webClientUtil;
  
  @Value("${server.ip}")
  String serverIP;
  
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
   * @param callbackUrl
   * @param ocrFiles
   * @param request
   * @throws Exception
   */
  public void setOcrProcess(String requestId, String callbackUrl, String format, MultipartFile[] ocrFiles, HttpServletRequest request) throws Exception {
    
    // 현재 시간
    LocalDateTime now = LocalDateTime.now();
    String formatNow  = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    
    // 1. DB NPSPEN0001, NPSPEN0002 PARAM SET
    Logger.info("1. DB NPSPEN0001, NPSPEN0002 PARAM SET");
    NpsPenHistoryDTO npsPenHistoryDTO = new NpsPenHistoryDTO();
    npsPenHistoryDTO.setRequestId(requestId);
    npsPenHistoryDTO.setCallbackUrl(callbackUrl);
    npsPenHistoryDTO.setFormat(format);
    npsPenHistoryDTO.setReqDt(formatNow);
    npsPenHistoryDTO.setResDt(formatNow);
    npsPenHistoryDTO.setRegDt(formatNow);
    
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
        analysisObj.put("callbackUrl", "http://"+serverIP+":9100/api/v1/aiocr/getOcrResult");
        JSONObject loadAnalysis = webClientUtil.post(
            "http://"+serverIP+":8080/twinreader-mgr-service/api/v1/analysis/inference/reqId"
            , analysisObj
            , JSONObject.class
        );
      }
      // 트윈리더 2.3 버전 - /twinreader-mgr-service/api/v2/flow/twrd
      else {
        Logger.info("7-2. 트윈리더 2.3버전 처리");
        analysisArr.add("/"+requestId+"/");
        analysisObj.put("pathList", analysisArr);
        analysisObj.put("requestID", requestId);
        analysisObj.put("callbackUrl", "http://"+serverIP+":9100/api/v1/aiocr/getOcrResult");
        analysisObj.put("pipelineName", pipelineName);
        analysisObj.put("clsfGroupID", clsfGroupID);
        
        JSONObject loadAnalysis = webClientUtil.post(
            "http://"+serverIP+":8080/twinreader-mgr-service/api/v2/flow/twrd"
            , analysisObj
            , JSONObject.class
        );
      }
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
    
    // 1. DB NPSPEN0001, NPSPEN0002 PARAM SET
    Logger.info("1. DB NPSPEN0001, NPSPEN0002 PARAM SET");
    NpsPen0002DTO npsPen0002DTO = new NpsPen0002DTO();
    npsPen0002DTO.setRequestId(requestId);
    
    ArrayList successPathList = (ArrayList) reqBody.get("successPathList");
    try {
      for(int i=0; i<successPathList.size(); i++) {
        JSONObject ocrObj   = new JSONObject();
        
        String imagePath    = (String) successPathList.get(i);
        String imageName    = imagePath.replace("/" + requestId + "/", "");
        String tmpPath      = imagePath.substring(0, imagePath.lastIndexOf(".")) + "_" + imagePath.substring(imagePath.lastIndexOf(".")+1);
        
        String filePath     = "/data/twinreader/data/output" + tmpPath + tmpPath.replace(requestId, "extractionResult") + "_extract_result.json";
        
        // 2. DB NPSPEN0001, NPSPEN0002 PARAM SET
        Logger.info("2. DB NPSPEN0001, NPSPEN0002 PARAM SET");
        npsPen0002DTO.setFileNm(imageName);
        
        // 3. 고객사 요청에 대한 DB 값 가져오기 (FORMAT)
        Logger.info("3. 고객사 요청에 대한 DB 값 가져오기 (FORMAT)");
        NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
        npsPen0001DTO.setRequestId(requestId);
        HashMap<String, Object> selectReqInfo = sqlSessionTemplate.selectOne("NpsPen0001Sql.selectReqInfo", npsPen0001DTO);
        String format         = (String) selectReqInfo.get("FORMAT");
        
        // 4. 항목 추출 결과 OUTPUT 경로에서 가져오기
        Logger.info("4. 항목 추출 결과 OUTPUT 경로에서 가져오기");
        File readFile         = new File(filePath);
        if(readFile.getParentFile().exists()) {
          BufferedReader br   = new BufferedReader((new InputStreamReader(new FileInputStream(readFile))));
          String strJson      = br.readLine();
          JSONParser par      = new JSONParser();
          JSONObject jsonObj  = (JSONObject) par.parse(strJson);
          
          JSONObject tmpObj = new JSONObject();
          
          Iterator pages = jsonObj.keySet().iterator();
          while(pages.hasNext()) {
            String pageNum = pages.next().toString();
            JSONObject pageObj  = (JSONObject) jsonObj.get(pageNum);
            JSONObject metaData  = (JSONObject) pageObj.get("metaData");
            
            // 5. DB NPSPEN0002 UPDATE or INSERT
            Logger.info("5. DB NPSPEN0002 UPDATE or INSERT");
            int pageNumber = Integer.parseInt(pageNum.replace("Page", ""));
            npsPen0002DTO.setPageNum(pageNumber);
            npsPen0002DTO.setCategory((String) metaData.get("classification"));
            npsPen0002DTO.setProStatus("success");
            npsPen0002DTO.setProMsg(null);
            
            if(pageNumber > 1) {
              Logger.info("##### NpsPen0002Sql.insertNpsPen0002 호출");
              Logger.info("##### npsPen0002DTO 값 : " + npsPen0002DTO.toString());
              sqlSessionTemplate.insert("NpsPen0002Sql.insertNpsPen0002", npsPen0002DTO);
            } else {
              Logger.info("##### NpsPen0002Sql.updateNpsPen0002 호출");
              Logger.info("##### npsPen0002DTO 값 : " + npsPen0002DTO.toString());
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
      Logger.error("##### SuccessPathList PROCESS FAILED " + error.getMessage());
      throw new Exception("SuccessPathList PROCESS FAILED");
    }
    
    // 9. 실패 건에 대해 처리
    Logger.info("9. 실패 건에 대해 처리");
    ArrayList failInfoList = (ArrayList) reqBody.get("failInfoList");
    try {
      for(int i=0; i<failInfoList.size(); i++) {
        JSONObject ocrObj   = new JSONObject();
        JSONObject pathObj  = (JSONObject) successPathList.get(i);
        
        String imagePath    = (String) pathObj.get("imagePath");
        String imageName    = imagePath.replace("/" + requestId + "/", "");
        
        // 10. 문서분류 결과 조회 - /twinreader-mgr-service/api/v1/analysis/category
        Logger.info("10. 문서분류 결과 조회");
        JSONObject jsonObject = new JSONObject();
        JSONArray jsonArray   = new JSONArray();
        jsonArray.add(imagePath);
        jsonObject.put("images", jsonArray);
        JSONArray analysisResultArr = webClientUtil.post(
            "http://"+serverIP+":8080/twinreader-mgr-service/api/v1/analysis/category"
            , jsonObject
            , JSONArray.class
        );
        JSONObject analysisResult = (JSONObject) analysisResultArr.get(0);
        
        // 11. DB NPSPEN0002 UPDATE
        Logger.info("11. DB NPSPEN0002 UPDATE");
        npsPen0002DTO.setFileNm(imageName);
        npsPen0002DTO.setPageNum(0);
        npsPen0002DTO.setCategory("분류실패");
        npsPen0002DTO.setProStatus((Boolean) analysisResult.get("success") ? "success": "failed");
        npsPen0002DTO.setProMsg((String) analysisResult.get("message"));
        sqlSessionTemplate.update("NpsPen0002Sql.updateNpsPen0002", npsPen0002DTO);
        
        ocrObj.put("fileNm", imageName);
        ocrObj.put("fileResult", new JSONObject());
        ocrResult.add(ocrObj);
      }
    } catch(Exception error) {
      Logger.error("##### FaileInfoList PROCESS FAILED " + error.getMessage());
      throw new Exception("FaileInfoList PROCESS FAILED");
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
    
    // 1. DB NPSPEN0001 PARAM SET
    Logger.info("1. DB NPSPEN0001 PARAM SET");
    NpsPen0001DTO npsPen0001DTO = new NpsPen0001DTO();
    npsPen0001DTO.setRequestId(requestId);
    
    // 2. 고객사 요청에 대한 DB 값 가져오기 (CALLBACK_URL)
    Logger.info("2. 고객사 요청에 대한 DB 값 가져오기 (CALLBACK_URL)");
    HashMap<String, Object> selectReqInfo = sqlSessionTemplate.selectOne("NpsPen0001Sql.selectReqInfo", npsPen0001DTO);
    String callbackUrl    = (String) selectReqInfo.get("CALLBACK_URL");
    
    // 3. CallbackUrl 호출, WebClient 객체 생성
    Logger.info("3. CallbackUrl 호출");
    JSONObject callbackResult = webClientUtil.post(
        callbackUrl
        , new JSONObject(result)
        , JSONObject.class
    );
    
    // 4. DB NPSPEN0001 UPDATE
    Logger.info("4. DB NPSPEN0001 UPDATE");
    npsPen0001DTO.setResDt(formatNow);
    sqlSessionTemplate.update("NpsPen0001Sql.updateNpsPen0001", npsPen0001DTO);
    
    Logger.info("##### CallbackUrl 호출 결과 : " + callbackResult);
    
  }
  
}
