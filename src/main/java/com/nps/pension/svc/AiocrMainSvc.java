package com.nps.pension.svc;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;

public interface AiocrMainSvc {
  public void setOcrProcess(String requestId, String callbackUrl, MultipartFile[] ocrFiles, HttpServletRequest request) throws Exception;
  public JSONArray getOcrResult(String requestId, JSONObject reqBody, HttpServletRequest request) throws Exception;
  public void callCallbackUrl(String requestId, HashMap<String, Object> result, HttpServletRequest request) throws Exception;
  public WebClient setWebClient(HttpServletRequest request, String baseUrl);
}
