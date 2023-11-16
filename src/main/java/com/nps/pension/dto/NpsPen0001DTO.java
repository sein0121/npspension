package com.nps.pension.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class NpsPen0001DTO {
  
  public String requestId;    // 리퀘스트ID
  public String callbackUrl;  // 콜백URL
  public String format;       // 포멧구조
  public String reqDt;        // 요청일시
  public String resDt;        // 처리일시
  public String regDt;        // 등록일시
  
}
