$.STRATUS = function (){
	
	var _init = function () {
		console.log('Loaded')
		$('#logout').click(_logout)
	};
	
	var _makeBasicAuth = function(user, password) {
		  var token = user + ':' + password;
		  var hash = Base64.encode(token);
		  return "Basic " + hash;
	};

	var _logout = function (event) {
		console.log('Logout called')
		event.preventDefault();
		
		var auth = _makeBasicAuth('invalid','');
		$.ajax({
		    url : 'https://192.168.56.101:8445/pswd/',
		    method : 'GET',
		    beforeSend : function(req) {
		        req.setRequestHeader('Authorization', auth);
		    }
		});
	};
	
	return {
		init: _init,
	};
}();

$(document).ready($.STRATUS.init);
