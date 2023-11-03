package com.nps.pension.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class NpsPen0002DTO {
  
  public String requestId;
  public String reqDt;
  public String fileNm;
  public int pageNum;
  public String category;
  public String proStatus;
  public String regDt;
  
}
