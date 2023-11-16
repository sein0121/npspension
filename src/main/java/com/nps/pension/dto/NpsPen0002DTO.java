package com.nps.pension.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class NpsPen0002DTO {
  
  public String requestId;    // 리퀘스트ID
  public String fileNm;       // 파일명
  public int pageNum;         // 페이지번호
  public String category;     // 분류명
  public String proStatus;    // 처리상태
  public String regDt;        // 등록일시
  
}
