// const path          = require("path");
// const axios         = require('axios');
// const fs            = require("fs");
// const xlsx          = require("xlsx");

const searchParams  = new URLSearchParams(location.search);
// let serverNo        = window.location.hostname;

/*
// 내부망
if(serverNo.substring(0,3) === '192') {
  $("input[name=serverType][value=in]").prop('checked', true);
  $("input[name=serverType][value=out]").prop('checked', false);
  changeServerNo();
}
// 외부망
else if(serverNo.substring(0,3) === '121') {
  $("input[name=serverType][value=in]").prop('checked', false);
  $("input[name=serverType][value=out]").prop('checked', true);
  changeServerNo();
}
*/

$("input[name=previewBtn][value=json]").prop('checked', true);
$("input[name=previewBtn][value=image]").prop('checked', false);

// $("select[name=serverNo] option[value='"+serverNo+"']").prop('selected', true);

for (let requestParam of searchParams) {
  if(requestParam[0] === 'requestId') $('#requestId').val(requestParam[1]);
  else if(requestParam[0] === 'schemaNm') $('#schemaNm').val(requestParam[1]);
}

if($('#requestId').val() !== '' && $('#schemaNm').val() !== '') searchResult();

$('#searchBtn').on('click', function() { searchResult(); });
$('#schemaNm').on('change', function() { searchResult(); });
$('#pluginYn').on('change', function() { searchResult(); });
// $('input[name=serverType]').on('change', function() { changeServerNo(); });

/*
$(document).on('change', 'input[name=previewBtn]', function() {
  if($("input[name=previewBtn]:checked").val() === 'json') {
    $('.imgViewer').css('display', 'none');
    $('.jsonViewer').css('display', 'block');
  }
  else if($("input[name=previewBtn]:checked").val() === 'image') {
    $('.imgViewer').css('display', 'block');
    $('.jsonViewer').css('display', 'none');
  }
});
*/

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

/*
$(document).on('click', 'tr', function(e) {
  // $('div.jsonViewer').html('');

  let requestId = $('#requestId').val();
  let imageName = $(this).find('td[class=ellipsis]').attr('title');

  // TODO tiff 파일도 화면에 뜰 수 있게 처리
  if($(this).find('td[class=successYn]').text() === '성공') {
    let imgReName = imageName?.substring(0, imageName?.lastIndexOf(".")) + '_' + imageName?.substring(imageName?.lastIndexOf(".")+1);

    // $('div.imgViewer').html('<a class="imgHref" href="/readData/input/'+requestId+'/'+imageName+'" target="_blank"><img src="/readData/input/'+requestId+'/'+imageName+'" onLoad={changeImgSize();}></a>');
    $('div.imgViewer').html('<a href="/readData/input/'+requestId+'/'+imageName+'" target="_blank" style="height: 0px;"><img src="/readData/output/'+requestId+'/'+imgReName+'/'+imgReName+'-001/original/'+imgReName+'-001.png'+'" onLoad={changeImgSize();}></a>');

    // TODO monaco editor 사용해서 json 화면에 출력하도록 수정
    $.ajax({
      url : '/readData/output/'+requestId+'/'+imgReName+'/extractionResult/'+imgReName+'_extract_result.json',
      dataType : 'json',
      processData : false,
      contentType : 'application/json; charset=UTF-8',
      type : 'GET',
      success : function(result) {
        // $('div.jsonViewer').html('<p class="jsonResult">'+JSON.stringify(result, null, 2)+'</p>');

        // 모나코 에디터 테스트
        $('#monaco').html('');
        require.config({paths: {'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.26.1/min/vs'}});
        require(["vs/editor/editor.main"], () => {
          monaco.editor.create(document.getElementById('monaco'), {
            value: JSON.stringify(result, null, 2),
            language: 'json',
            theme: 'vs-dark',
            lineNumbers: 'on',            // 줄 번호
            glyphMargin: false,           // 체크 이미지 넣을 공간이 생김
            vertical: 'auto',
            horizontal: 'auto',
            verticalScrollbarSize: 10,
            horizontalScrollbarSize: 10,
            scrollBeyondLastLine: false,  // 에디터상에서 스크롤이 가능하게
            readOnly: true,              // 수정 가능 여부
            automaticLayout: true,        // 부모 div 크기에 맞춰서 자동으로 editor 크기 맞춰줌
            minimap: {
              enabled: true            // 우측 스크롤 미니맵
            },
            lineHeight: 19
          });
        });
      },
      error : function(e) {
        alert("처리에 실패하였습니다.", e);
      }
    });
  }
});
 */

function changeImgSize() {
  // 이미지 사이즈 조정
  let width = $('img').width();
  let height = $('img').height();

  let imgViewerWidth = $('.imgViewer').width();
  let imgViewerHeight = $('.imgViewer').height();

  if(width > height)  $('img').css('height', imgViewerHeight);
  else $('img').css('width', imgViewerWidth);
}

function enterKey() {
  if(window.event.keyCode == 13) searchResult();
}

/*
function changeServerNo() {
  if($("input[name=serverType]:checked").val() === 'in') {
    $('#server210').val("192.168.100.210");
    $('#server211').val("192.168.100.211");
    $('#server170').val("192.168.100.170");
  } else {
    $('#server210').val("121.134.174.225");
    $('#server211').val("121.134.174.154");
    $('#server170').val("121.134.174.155");
  }
}
*/

function searchResult() {
  let requestId = $('#requestId').val();
  let schemaNm  = $('#schemaNm').val();
  // let serverNo  = $("select[name=serverNo] option:selected").val();
  let serverNo  = window.location.hostname;
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
      url : 'http://'+serverNo+':8080/twinreader-mgr-service/api/v1/analysis/category',
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
          let url       = 'http://'+serverNo+':8080/twinreader-extn-service/api/v1/extract/visualization?pluginRun='+pluginYn+'&&schemaName='+schemaNm+'.json&imagePath='+ obj?.path +'&pageIndex='+obj?.pageNumber;

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
