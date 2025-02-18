package com.national.pension.svc;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;

public interface AiocrSyncSvc {
  
  public void checkRequestId(String requestId, String type) throws Exception;
  public void setOcrProcess(String requestId, String format, HttpServletRequest request) throws Exception;
//  public void setOcrProcess(String requestId, String format, MultipartFile[] filename, MultipartHttpServletRequest request) throws Exception;
  public void setProStatus(String requestId, JSONObject reqBody, HttpServletRequest request) throws Exception;
  public void getProStatus(String requestId) throws Exception;
  public JSONArray getOcrResult(String requestId, HttpServletRequest request) throws Exception;
  public void deleteDirectory(String requestId) throws Exception;
  
}
