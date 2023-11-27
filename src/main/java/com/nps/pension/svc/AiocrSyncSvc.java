package com.nps.pension.svc;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

public interface AiocrSyncSvc {
  
  public void checkRequestId(String requestId) throws Exception;
  public void setOcrProcess(String requestId, String format, MultipartFile[] ocrFiles, HttpServletRequest request) throws Exception;
  public void setProStatus(String requestId, JSONObject reqBody, HttpServletRequest request) throws Exception;
  public void getProStatus(String requestId) throws Exception;
  public JSONArray getOcrResult(String requestId, HttpServletRequest request) throws Exception;
  public void deleteDirectory(String requestId) throws Exception;
  
}
