package com.nps.pension.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class NpsPenHistoryDTO {
  
  public String requestId;    // PK 리퀘스트ID
  public String reqDt;        // PK 요청일시
  
  // NPSPEN0001
  public String callbackUrl;  // 콜백URL
  public String resDt;        // 처리일시
  
  // NPSPEN0002
  public String fileNm;       // 파일명
  public int pageNum;         // 페이지번호
  public String category;     // 분류명
  public String proStatus;    // 처리상태
  
  public String regDt;        // 등록일시
  
}
