/**
 * KNOB circles initialization
 */

$("#inner-circle").knob({
	"readOnly": true,
	"width": 223,
	"height": 223,
	"thickness": 0.1,
	"displayInput": false,
	"fgColor": "#e6eff1"
});

$("#outer-circle").data({ "width": 265 }).knob({
	"readOnly": true,
	"width": 265,
	"height": 265,
	"thickness": 0.13,
	"displayInput": false,
	"fgColor": "#a59b9e"
});

$("#outer-circle-cursor").knob({
	"cursor": 0.5,
	"readOnly": true,
	"width": 303,
	"height": 303,
	"thickness": 0.24,
	"displayInput": false,
	"fgColor":"#898989"
});

$("#first-circle").data({ "width": 165 }).knob({
	"readOnly": true,
	"width": 165,
	"height": 165,
	"thickness": 0.14,
	"displayInput": false,
	"fgColor": "#efefef"
});

$("#second-circle").data({ "width": 165 }).knob({
	"readOnly": true,
	"width": 165,
	"height": 165,
	"thickness": 0.14,
	"displayInput": false,
	"fgColor": "#e0ded5"
});

$("#third-circle").data({ "width": 165 }).knob({
	"readOnly": true,
	"width": 165,
	"height": 165,
	"thickness": 0.14,
	"displayInput": false,
	"fgColor": "#b6aeb0"
});

$("#first-circle-cursor, #second-circle-cursor, #third-circle-cursor").knob({
	"cursor": 0.5,
	"readOnly": true,
	"width": 189,
	"height": 189,
	"thickness": 0.25,
	"displayInput": false,
	"fgColor":"#898989"
});

function getConverted(column) {
	var firstitem, seconditem;
	for (var key in column) {
		switch (column[key].currency) {
			case "RUB": column[key].converted = column[key].amount;
				break;
			case "EUR": column[key].converted = (column[key].amount * global.eur).toFixed(3);
				break;
			case "USD": column[key].converted = (column[key].amount * global.usd).toFixed(3);
				break;
			case "JPY": column[key].converted = (column[key].amount * global.jpy).toFixed(3);
				break;
		}
		switch (column[key].period) {
			case "MONTH": break;
			case "HOUR": column[key].converted = (column[key].converted * 730).toFixed(3);
				break;
			case "DAY": column[key].converted = (column[key].converted * 30.41666667).toFixed(3);
				break;
			case "QUARTER": column[key].converted = (column[key].converted / 3).toFixed(3);
				break;
			case "YEAR": column[key].converted = (column[key].converted / 12).toFixed(3);
				break;
		}
		switch (user.checkedCurr) {
			case "RUB": break;
			case "EUR": column[key].converted = (column[key].converted / global.eur).toFixed(3);
				break;
			case "USD": column[key].converted = (column[key].converted / global.usd).toFixed(3);
				break;
			case "JPY": column[key].converted = (column[key].converted / global.jpy).toFixed(3);
				break;
		}
		if (column == incomes) {
			if (firstitem == undefined) {firstitem = key; $("#incomeslider").data("firstitem", key); }
			incomesSumMonth += Math.round(column[key].converted);
			$("#circle-select-1, #circle-select-2, #circle-select-3").append('<option value="' + parseInt(column[key].income_id, 10) + '">' + escape(column[key].title) + '</option>');
		}
		else {
			if (firstitem == undefined) {firstitem = key; $("#expenseslider").data("firstitem", key); }
			else if (seconditem == undefined) {seconditem = key; $("#expenseslider").data("seconditem", key); }
			expensesSumMonth += Math.round(column[key].converted);
			$("#circle-select-1, #circle-select-2, #circle-select-3").append('<option value="' + (parseInt(column[key].expense_id, 10) + 100) + '">' + escape(column[key].title) + '</option>');
		}
	}

	if ( Math.abs(incomesSumMonth-expensesSumMonth) < 10 ) {
		expensesSumMonth = incomesSumMonth;
	}

	$('select').trigger('refresh');
}

function initStatisticPage() {

	changeCurrency = function () {
		function rubRate(currency) {
			switch (currency) {
				case "EUR": return global.eur;
				case "USD": return global.usd;
				case "JPY": return global.jpy;
				default: return 1;
			}
		}

		user.lastCurr = user.checkedCurr;
	};

	changeCurrency();

	initCircle = function (whichcircle, item, beforevalue) {
		var sum, value, circle;
		$("#" + whichcircle + "circlediv").show();
		if ( item.hasOwnProperty("income_id") ) {
			sum = incomesSumMonth;
			value = item.income_id;
		}
		else {
			sum = expensesSumMonth;
			value = parseInt(item.expense_id, 10) + 100;
		}
		switch (whichcircle) {
			case "first": animatecircle.call($("#first-circle"), beforevalue, (item.converted / 730).toFixed(2), (sum / 730), item.title );
				circle = 1;
				$("#first-circle").data({"item": item, "sum": sum});
				break;
			case "second": animatecircle.call($("#second-circle"), beforevalue, (item.converted / 30.41666667).toFixed(1), (sum / 30.41666667), item.title );
				circle = 2;
				$("#second-circle").data({"item": item, "sum": sum});
				break;
			case "third": animatecircle.call($("#third-circle"), beforevalue, Math.round(item.converted * 1.2) * 10, (sum * 1.2) * 10, item.title );
				circle = 3;
				$("#third-circle").data({"item": item, "sum": sum});
				break;
		}
		$("#circle-select-" + circle).find('option').removeAttr("selected");
		setTimeout( function() { $("#circle-select-" + circle +" option[value=" + value + "]").attr('selected','selected'); $('select').trigger('refresh'); } , 100)
		$("#circle-select-" + circle + "-back").removeClass().addClass(item.icon);
	}

	if (incomes[ $("#incomeslider").data("firstitem") ] != undefined && expenses[ $("#expenseslider").data("firstitem") ] != undefined) {
		initCircle("first", incomes[ $("#incomeslider").data("firstitem") ], 0);
		initCircle("second", expenses[ $("#expenseslider").data("firstitem") ], 0);
		if ( $("#expenseslider").data("seconditem") !== undefined ) { initCircle("third", expenses[ $("#expenseslider").data("seconditem") ], 0); }
		else initCircle("third", expenses[ $("#expenseslider").data("firstitem") ], 0);
	}

	$("#expenses-lines-container").html('<div class="lines-title"> Expenses structure <span class="grey">(' + separateNumber(expensesSumMonth) + '<span class="greysmall"><span class="greysmallval curr"></span>/Month</span>)<span></span></div>')
	$("#incomes-lines-container").html('<div class="lines-title"> Incomes structure <span class="grey">(' + separateNumber(incomesSumMonth) + '<span class="greysmall"><span class="greysmallval curr"></span>/Month</span>)<span></span></div>')

	var incomesIdSorted = Object.keys(incomes).sort(function(a,b){return incomes[b].converted - incomes[a].converted});
	var expensesIdSorted = Object.keys(expenses).sort(function(a,b){return expenses[b].converted - expenses[a].converted});

	initlines = function(column) {
		var container, idSorted, maxConvertedId, i;
		if (column == incomes) {container = "#incomes-lines-container"; idSorted = incomesIdSorted; maxConvertedId = incomesIdSorted[0]; sum = incomesSumMonth; i=0}
		else {container = "#expenses-lines-container"; idSorted = expensesIdSorted; maxConvertedId = expensesIdSorted[0]; sum = expensesSumMonth; i=100}

		idSorted.forEach(function(id) {
			$(container).append('<div id="line-' + i + '" class="itemline"><span class="lineitemtitle lightcircletitle">' + column[id].title + '</span><div class="lineitempercent">'+ Math.round(100 * column[id].converted / sum) +'%</div><div class="lineitemvalue">' + separateNumber(Math.round(column[id].converted)) + ' <span class="boldcircletitle curr lineitemcurr"></span><span class="lightcircletitle lineitemcurr">/Month</span></div><div class="leftpoint"></div><div id="linebackground-'+ i +'" class="itemlinebackground"></div></div>');
			$("#line-" + i).data({"item": column[id]}).css({"width": Math.round(100 * column[ id ].converted / column[maxConvertedId].converted ) + "%"});
			$("#linebackground-" + i).addClass(column[id].icon);
			i++;
		});
		$("#line-0, #line-100").addClass("activeline");
	}

	initlines(incomes);
	initlines(expenses);

	// FUTURE SAVINGS: balances are projected by account-service, here they are only converted and drawn
	initSavingsCircles = function (animatetime) {

		var projections = savingsProjection || [],
				monthSavings = [],
				beforevalue = Number($("#after-savings-value").data("lastvalue")) || 0,
				beforemiddlevalue = Number($("#after-savings-value").data("lastmiddlevalue")) || 0,
				listHtml = "";

		for (var m = 0; m < 13; m++) {
			monthSavings[m] = 0;
		}

		$.each(projections, function (index, projection) {
			var current = toCheckedCurrency(projection.current, projection.currency),
					balances = projection.balances || [],
					last = balances.length ? toCheckedCurrency(balances[balances.length - 1], projection.currency) : current;
			monthSavings[0] += current;
			for (var k = 1; k < 13; k++) {
				monthSavings[k] += k <= balances.length ? toCheckedCurrency(balances[k - 1], projection.currency) : current;
			}
			listHtml += '<div class="projection-row" data-id="' + escape(projection.savingId) + '">'
				+ '<span class="projection-name">' + escape(projection.name) + ' <span class="projection-type">' + escape((projection.type || "").toLowerCase()) + '</span></span>'
				+ '<span class="projection-values">' + separateNumber(Math.round(current)) + ' &rarr; <b>' + separateNumber(Math.round(last)) + '</b>' + ($(".curr").first().data("curr") || "") + '</span>'
				+ '</div>';
		});
		$("#savings-projection-list").html(listHtml);

		var aftervalue = monthSavings[12],
				growth = aftervalue - monthSavings[0];

		// Chart slope: decreasing, flat or rising in proportion to growth
		var chartposition = growth < 0 ? 175 : growth === 0 ? 155 : 110 - Math.min(1, growth / Math.max(Math.abs(monthSavings[0]), 1)) * 95;
		$({ value: 91 }).animate({ value: chartposition }, {
			duration: animatetime,
			easing: 'swing',
			step: function () {
				drawChartLine(this.value);
			}
		});

		$("#before-savings-value").html( separateNumber( Math.round(monthSavings[0]) ) );
		$("#after-savings-value").data({"lastvalue": aftervalue, "lastmiddlevalue": monthSavings[6]});

		// Set font-size in big circle
		(function() {
			if (Math.abs (aftervalue) < 9999999 ) {
				$("#after-savings-value").css({"font-size": "48px"})
			}
			else if (Math.abs (aftervalue) < 99999999) {
				$("#after-savings-value").css({"font-size": "42px"})
			}
			else {
				$("#after-savings-value").css({"font-size": "37px"})
			}
			if (Math.abs (monthSavings[0]) > 9999999 ) {
				$("#before-savings-value").css({"font-size": "26px"})
			}
		})();

		// Animate last value on chart
		$({ value: beforevalue }).animate({ value: aftervalue }, {
			duration: animatetime,
			easing: 'linear',
			step: function () {
				$("#after-savings-value").html(separateNumber( Math.round(this.value)));
				$("#month12").children(".large-month-val").children(".val").html(separateNumber(Math.round(this.value)));
			}
		});
		// Animate middle value on chart
		$({ value: beforemiddlevalue }).animate({ value: monthSavings[6] }, {
			duration: animatetime,
			easing: 'linear',
			step: function () {
				$("#month6").children(".large-month-val").children(".val").html(separateNumber( Math.round(this.value)));
			}
		});
		// Set precision value
		setTimeout(function() {
			$("#after-savings-value").html(separateNumber( Math.round(aftervalue)));
			$("#month12").children(".large-month-val").children(".val").html(separateNumber(Math.round(aftervalue)));
			$("#month6").children(".large-month-val").children(".val").html(separateNumber(Math.round(monthSavings[6])));
		}, animatetime+20);

		// Set every month values on chart
		for (var i = 1; i < 12; i++) {
			$("#month" + i).children(".month-val").children(".val").html(separateNumber(Math.round(monthSavings[i])));
		}
		$("#month6, #month12").children(".month-val").html("");
	};

	// Change currency stuff
	$("#rubcurr, #eurcurr, #usdcurr").removeClass("currchecked");
	switch (user.checkedCurr) {
		case "RUB": $(".curr").html(" Rub ").data("curr", " rub."); $(".savings-circle-currency").html(" Rubles "); $("#rubcurr").addClass("currchecked");
			break;
		case "EUR": $(".curr, .savings-circle-currency").html(" Eur ").data("curr", " \u20ac"); $("#eurcurr").addClass("currchecked");
			break;
		case "USD": $(".curr, .savings-circle-currency").html(" USD ").data("curr", " $"); $("#usdcurr").addClass("currchecked");
			break;
	}

	if (incomesSumMonth > expensesSumMonth) {
		setTimeout(function() {
			$(".topmaincircletitle").html("Spare");
			simpleanimatecircle.call($("#inner-circle"), 0, 100, 1200);
			$("#outer-circle-value").css({"color": "black"});
			$("#incomes-cursor").hide();
			$("#expense-cursor").show();
			animatecircle.call($("#outer-circle"), 0, Math.round(expensesSumMonth), Math.round(incomesSumMonth));}, 350);
	}
	else {
		setTimeout(function() {
			$(".topmaincircletitle").html("Loss");
			simpleanimatecircle.call($("#inner-circle"), 0, 100, 800);
			$("#outer-circle-value").css({"color": "#eaa7a7"});
			$("#expense-cursor").hide();
			$("#incomes-cursor").show();
			animatecircle.call($("#outer-circle"), 0, Math.round(incomesSumMonth), Math.round(expensesSumMonth)); }, 350);
	}
}

function separateNumber(val) {
	if (Math.abs (val) > 999) {
		val = val.toString().replace(/(\d)(?=(\d\d\d)+(?!\d))/g, '$1 <span class="lightcircletitle">');
	}
	else {
		val = val.toString().replace(/(\.)/g, '<span class="lightcircletitle">$1');
	}
	return val;
};

// ANIMATE CIRCLE PROCESS
function animatecircle(beforevalue, aftervalue, sum, title) {
	var before = (100 * beforevalue/sum),
			after = (100 * aftervalue/sum),
			R = $(this).data("width") / 2,
			alpha = (after * 0.02 * Math.PI),	// percent to radians
			n = 10,								// margin from circle
			id = $(this).attr("id"),
			$this = $(this),
			ycord, xcord;

	// Set font size
	(function() {
		var fontpx;
		if (id == "outer-circle") {
			if (sum-aftervalue < 999999) { fontpx = 48 }
			else if (sum-aftervalue < 9999999) {fontpx = 37 }
			else { fontpx = 22 }
		}
		else {
			if (aftervalue < 99999) {fontpx = 31}
			else if (aftervalue < 999999) {	fontpx = 27 }
			else { fontpx = 18 }
		}
		$("#" + id + "-value").css({"font-size": fontpx + "px"})
	})();

	// Percent value location
	if (after <= 25 ) {
		ycord = Math.round( R + (n / 2) - (R * Math.sin(1.57 - alpha)) );
		xcord = Math.round( R + (n) + (R * Math.cos(1.57 - alpha)) );
		animatetime = 500;
	}
	else if (after <= 50 ) {
		ycord = Math.round( R + (n / 2) + (R * Math.sin(alpha - 1.57)) );
		xcord = Math.round( R + (n * 2) + (R * Math.cos(alpha - 1.57)) );
		animatetime = 1400;
	}
	else if (after <= 75 ) {
		ycord = Math.round( R - (n * 2) + (R * Math.sin(alpha - 1.57)) );
		xcord = Math.round( R - (n * 4) + (R * Math.cos(alpha - 1.57)) );
		animatetime = 1500;
	}
	else {
		ycord = Math.round( R + (- n * 3) - (R * Math.sin(1.57 - alpha)) );
		xcord = Math.round( R + (- n * 3) + (R * Math.cos(1.57 - alpha)) );
		animatetime = 1600;
	}

	// Set and show percent value next to cursor
	$("#" + id + "-percent").hide();
	$("#" + id + "-percent").empty().append('<span class="lightcircletitle">' + Math.round(after) + '%</span>');
	setTimeout(function () { $("#" + id + "-percent").css({"left": xcord, "top": ycord}).fadeIn(400)}, animatetime-200);

	// Animate circle with its cursor
	$({ value: before }).animate({ value: after }, {
		duration: animatetime,
		easing: 'swing',
		step: function () {
			$this.val(this.value).trigger('change');
			$("#" + id + "-cursor").val(this.value).trigger('change');
		}
	});

	// Set precision value
	(function() {
		if (id == "outer-circle") {
			$("#" + id + "-value").html(separateNumber( Math.abs(Math.round((sum-aftervalue) / 10) * 10) ) );
		}
		else {
			$("#" + id + "-value").html( separateNumber(aftervalue) )
			$("#" + id + "-title").html(title);
		}
	})();
}

// Animate without percent moving and value growing (for inner circle)
function simpleanimatecircle(before, after, duration) {
	var $this = $(this);
	$({ value: before }).animate({ value: after }, {
		duration: duration,
		easing: 'swing',
		step: function () {
			$this.val(this.value).trigger('change');
		}
	});
}

/**
 * EVENT HANDLERS
 */

// Currency buttons
$(".currunchecked").click(function() {
	if (this.id == "eurcurr") {
		user.checkedCurr = "EUR";
	}
	else if (this.id == "usdcurr") {
		user.checkedCurr = "USD";
	}
	else {
		user.checkedCurr = "RUB";
	}
	runConvert();

	initStatisticPage();
	setTimeout(function() { initSavingsCircles(700) }, 100);
});

$("#outermaindiv, #incomes-cursor, #expense-cursor, .lines-title").click(function() {
	setTimeout(function() { $("#expenses-lines-container, #incomes-lines-container").toggle(); }, 250);
	$("#expense-cursor, #incomes-cursor").toggle(300);
	$(".itemline").removeClass("activeline");
	$("#line-0, #line-100").addClass("activeline");
});

$("#expenses-lines-container, #incomes-lines-container").on("hover", ".itemline", function() {
	$(".itemline").removeClass("activeline");
	$(this).addClass("activeline");
});

$("#expenses-lines-container, #incomes-lines-container").on("click", ".itemline", function() {
	$(".itemline").removeClass("activeline");
	$(this).addClass("activeline");
	var item = $(this).data("item");
	initCircle("first", item, 0);
	initCircle("second", item, 0);
	initCircle("third", item, 0);
});

$("#firstcirclediv, #secondcirclediv, #thirdcirclediv").click(function() {
	$("#" + this.id + "flipper").toggleClass("flippedcard");
});

$("#circle-select-1, #circle-select-2, #circle-select-3").on("change", function() {
	var column, item,
			circle = this.id,
			whichCircle;
	switch (circle) {
		case "circle-select-1": whichCircle = "first";
			break;
		case "circle-select-2": whichCircle = "second";
			break;
		default: whichCircle = "third";
			break;
	}
	if ($(this).val() < 100 ) {
		column = incomes;
		item = $(this).val();
		setTimeout(function() { initCircle( whichCircle, column[item], 0) }, 300);
	}
	else {
		column = expenses;
		item = $(this).val() - 100;
		setTimeout(function() { initCircle( whichCircle, column[item], 0) }, 300);
	}
	$("#" + circle + "-back").removeClass().addClass( column[item].icon );
});

var savingsProjection = null;

function rubPerUnit(currency) {
	switch (currency) {
		case "EUR": return global.eur;
		case "USD": return global.usd;
		case "JPY": return global.jpy;
		default: return 1;
	}
}

function toCheckedCurrency(amount, currency) {
	return Number(amount) * rubPerUnit(currency) / rubPerUnit(user.checkedCurr);
}

// Loads per-saving 12-month projections computed by account-service
function loadSavingsProjection(animatetime) {
	var token = getOauthTokenFromStorage(),
			headers = (global.savePermit && token) ? {'Authorization': 'Bearer ' + token} : {};
	$.ajax({
		url: 'accounts/' + encodeURIComponent(user.login) + '/savings/projection?months=12',
		datatype: 'json',
		type: 'get',
		headers: headers,
		success: function (data) {
			savingsProjection = data;
			initSavingsCircles(animatetime);
		},
		error: function () {
			savingsProjection = [];
			$("#savings-projection-list").html('<div class="projection-error">Projection is unavailable right now</div>');
		}
	});
}

(function(){
	var canvas1 = document.getElementById("movingcircle-1"),
			canvas2 = document.getElementById("movingcircle-2"),
			canvas3 = document.getElementById("movingcircle-3"),
			ctx1 = canvas1.getContext('2d'),
			ctx2 = canvas2.getContext('2d'),
			ctx3 = canvas3.getContext('2d'),
			x = canvas1.width / 2,
			y = canvas1.height / 2,
			radius = 99,
			startAngle = 1 * Math.PI,
			endAngle = 2.2 * Math.PI,
			counterClockwise = false;

	ctx1.beginPath();
	ctx1.arc(x, y, radius, startAngle, endAngle, counterClockwise);
	ctx1.lineWidth = 3;
	ctx1.strokeStyle = "#f2f2f2";
	ctx1.stroke();

	ctx2.beginPath();
	ctx2.arc(x, y, radius, startAngle, endAngle, counterClockwise);
	ctx2.lineWidth = 3;
	ctx2.strokeStyle = "#f2f2f2";
	ctx2.stroke();

	ctx3.beginPath();
	ctx3.arc(x, y, radius, startAngle, endAngle, counterClockwise);
	ctx3.lineWidth = 3;
	ctx3.strokeStyle = "#f2f2f2";
	ctx3.stroke();
})();

(function(){
	var canvas = document.getElementById("horizontal"),
			currentDate = new Date(),
			currentMonth = currentDate.getMonth(),
			allMonths = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
	if (canvas.getContext) {
		var ctx = canvas.getContext("2d");
		ctx.canvas.width  = $("#savingschart").width();
		ctx.canvas.height = $("#savingschart").height();
		for (i=0; i < 11; i++){
			ctx.beginPath();
			ctx.moveTo(0, 1 + i * 22);
			ctx.lineTo($("#savingschart").width(), 1 + i * 22);
			ctx.strokeStyle = '#e5e5e5';
			ctx.stroke();
			ctx.beginPath();
			ctx.moveTo(0, 0.5 + i * 22);
			ctx.lineTo($("#savingschart").width(), 0.5 + i * 22);
			ctx.strokeStyle = '#FFF';
			ctx.stroke();
		}
	}
	for (i = 0, j = currentMonth; i <=12; i++, j++) {
		if (j == 12) {j = 0}
		$("#month" + i).children(".month-name").html(allMonths[j]);
	}
})();

function drawChartLine(position) {
	var canvas = document.getElementById("chartline");
	var chartWidth = $("#savingschart").width(),
			chartHeight = $("#savingschart").height(),
			segmentWidth = $(".months").width();
	if (canvas.getContext){
		var ctx = canvas.getContext("2d");
		ctx.canvas.width  = $("#savingschart").width();
		ctx.canvas.height = $("#savingschart").height();
		ctx.beginPath();
		ctx.moveTo(0, 155);
		ctx.lineTo($("#savingschart").width(), position);
		ctx.strokeStyle = '#bed8db';
		ctx.stroke();
	}
	for (i = 0, j = 12; i < 12; i++, j--) {
		$("#month" + j).css({"height": chartHeight - position - i * 7 -((i * segmentWidth * (chartHeight - 153 - position) ) / chartWidth) });
	}
}
