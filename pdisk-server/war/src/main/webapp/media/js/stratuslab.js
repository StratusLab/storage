$.STRATUS = function (){
	
	var _init = function () {
		$('#logout').click(_logout)
	};

	var _logout = function (event) {
		event.preventDefault();
		// Workaround to logout user 
		// As HTTP is state-less there is no cross-browser clean way to do
		$.get(location.href.replace('://', '://x-pdisk-logout@'));
	};
	
	return {
		init: _init,
	};
}();

$(document).ready($.STRATUS.init);
