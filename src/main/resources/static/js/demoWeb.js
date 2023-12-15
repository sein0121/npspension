$("input[name=previewBtn][value=json]").prop('checked', true);
$("input[name=previewBtn][value=image]").prop('checked', false);

// requestId 리스트 가져오기
let requestList = $('#requestList').val();
requestList = JSON.parse(requestList);
requestList = requestList.sort((a, b) => {
  let aT = a.time.substring(0, 19);
  aT = aT.replace('T', '').replace(/-/gi, '').replace(/:/gi, '');

  let bT = b.time.substring(0, 19);
  bT = bT.replace('T', '').replace(/-/gi, '').replace(/:/gi, '');

  if(aT-bT > 0) return -1;
  else if(aT-bT === 0) return aT.name - bT.name;
  else return 1;
});
let reqHtml = '';
requestList.forEach((req) => {
  reqHtml += '<option value="'+req.name+'">'+req.name+'</option>';
});
$('#reqList').html(reqHtml);
if($('#requestId').val() !== '') $("select[name=reqList] option[value='"+$('#requestId').val()+"']").prop('selected', true);
$(document).on('change', 'select[name=reqList]', function() {
  $('#requestId').val($('select[name=reqList] option:selected').val());
});

// schemaList 리스트 가져오기
let schemaList = $('#schemaNmList').val();
schemaList = JSON.parse(schemaList);
schemaList = schemaList.sort((a, b) => {
  let aT = a.time.substring(0, 19);
  aT = aT.replace('T', '').replace(/-/gi, '').replace(/:/gi, '');

  let bT = b.time.substring(0, 19);
  bT = bT.replace('T', '').replace(/-/gi, '').replace(/:/gi, '');

  if(aT-bT > 0) return -1;
  else if(aT-bT === 0) return aT.name - bT.name;
  else return 1;
});
let schemaHtml = '';
schemaList.forEach((req) => {
  schemaHtml += '<option value="'+req.name.replace('.json', '')+'">'+req.name.replace('.json', '')+'</option>';
});
$('#schemaList').html(schemaHtml);
if($('#schemaNm').val() !== '') $("select[name=schemaList] option[value='"+$('#schemaNm').val()+"']").prop('selected', true);
$(document).on('change', 'select[name=schemaList]', function() {
  $('#schemaNm').val($('select[name=schemaList] option:selected').val());
});

if($('#requestId').val() !== '' && $('#schemaNm').val() !== '') searchResult();

$('#searchBtn').on('click', function() { searchResult(); });
$('#pluginYn').on('change', function() { searchResult(); });

// 테이블 tr 라인 배경색 변경
$(document).on('click', '.htmlLink', function(e){
  let htmlLinkId = $(this).attr('id');
  $('#'+htmlLinkId).parent().parent().attr('class', 'confirm');
  $('#'+htmlLinkId).parent().parent().css('background-color', '#F1F1F1');
});

$(document).on('mouseover', 'table#requestResult tr', function() {
  $(this).css('background-color', '#8693c140');
  // if($(this).attr('class') !== 'confirm') $(this).css('background-color', '#F1F1F1');
});

$(document).on('mouseleave', 'table#requestResult tr', function() {
  if($(this).attr('class') !== 'confirm' && $(this).attr('class') !== 'failed') $(this).css('background-color', '#FFFFFF');

  if($(this).attr('class') === 'confirm') $(this).css('background-color', '#F1F1F1');
  if($(this).attr('class') === 'failed') $(this).css('background-color', '#C1868E54');
});

function enterKey() {
  if(window.event.keyCode == 13) searchResult();
}

function searchResult() {
  let requestId = $('#requestId').val();
  let schemaNm  = $('#schemaNm').val();
  let serverIP  = $('#serverIP').val();
  let twinPort  = $('#twinPort').val();
  let pluginYn  = $('#pluginYn').is(':checked');

  let params = {"images":["/"+requestId+"/"]};

  if(requestId === '') {
    alert('리퀘스트ID를 입력하세요.');
    $('#requestId').focus();
  }
  else if(schemaNm === '') {
    alert('스키마명을 입력하세요.');
    $('#schemaNm').focus();
  }
  else {
    $.ajax({
      url : 'http://'+serverIP+twinPort+'/twinreader-mgr-service/api/v1/analysis/category',
      data: JSON.stringify(params),
      dataType : 'json',
      processData : false,
      contentType : 'application/json; charset=UTF-8',
      type : 'POST',
      success : function(result) {
        document.getElementById("findTr").style.display = "";

        // path(파일위치+파일명) 기준 sort
        // result = result.sort(function(a, b) {
        //   // path의 숫자 기준 sort
        //   return (Number(a?.path?.match(/(\d+)/g)[0]) - Number((b?.path?.match(/(\d+)/g)[0])));
        // });

        let tableHTML = '';
        result?.forEach((obj, idx)=>{
          let imgName   = obj?.path?.substr(obj?.path?.lastIndexOf('/')+1);
          let successYn = obj?.success || obj?.success === 'true' ? "성공" : "실패";
          let url       = 'http://'+serverIP+twinPort+'/twinreader-extn-service/api/v1/extract/visualization?pluginRun='+pluginYn+'&&schemaName='+schemaNm+'.json&imagePath='+ obj?.path +'&pageIndex='+obj?.pageNumber;

          if(successYn === "성공") tableHTML += '<tr style="background-color: white;" tabindex='+idx+'>';
          else tableHTML += '<tr class="failed" style="background-color: #C1868E54;">';
          // tableHTML += '<tr>';
          tableHTML += '<td class="resultNo"    style="width:50px; text-align:center; vertical-align: middle;">'+Number(idx+1)+'</td>';
          tableHTML += '<td class="category"    style="width:200px; text-align:left; vertical-align: middle;" title='+obj?.category+'><div class="categoryT" style="width:180px;">'+obj?.category+'</div></td>';
          tableHTML += '<td class="ellipsis"    style="width:280px;" title='+imgName+'>'+imgName+'</td>';
          tableHTML += '<td class="pageNumber"  style="width:100px; text-align:center; vertical-align: middle;">'+obj?.pageNumber+'</td>';
          tableHTML += '<td class="successYn"   style="width:120px; text-align:center; vertical-align: middle;">'+successYn+'</td>';
          if(successYn !== "실패") tableHTML += '<td class="resultLink"  style="width:150px; text-align:center; vertical-align: middle;"><a class="htmlLink" id="htmlLink'+idx+'" href="'+url+'" target="_blank">바로가기</a></td>';
          else tableHTML += '<td class="resultLink"  style="width:150px; text-align:center; vertical-align: middle;"></td>'
          tableHTML += '</tr>';
        });
        $('table#requestResult>tbody').html(tableHTML);
      },
      error : function(e) {
        alert("처리에 실패하였습니다.", e);
      }
    });
  }
}

document.addEventListener('DOMContentLoaded', function() {
  // 검색창 element를 id값으로 가져오기
  const payrollSearch = document.querySelector('#findNm');
  // 테이블의 tbody element를 id값으로 가져오기
  const payrollTable = document.querySelector('#requestResult tbody');

  //검색창 element에 keyup 이벤트 세팅. 글자 입력 시 마다 발생.
  payrollSearch.addEventListener('keyup', function() {
    // 사용자가 입력한 검색어의 value값을 가져와 소문자로 변경하여 filterValue에 저장
    const filterValue = payrollSearch.value.toLowerCase();
    // 현재 tbody안에 있는 모든 tr element를 가져와 rows에 저장
    const rows = payrollTable.querySelectorAll('tr');

    //tr들 for문으로 순회
    for (var i = 0; i < rows.length; i++) {
      // 현재 순회중인 tr의 textContent를 소문자로 변경하여 rowText에 저장
      var rowText = rows[i].textContent.toLowerCase();
      // rowText가 filterValue를 포함하면, 해당 tr은 보여지게 하고, 그렇지 않으면 숨긴다.
      if (rowText.includes(filterValue)) {
        rows[i].style.display = '';
      } else {
        rows[i].style.display = 'none';
      }
    }
  });
});
