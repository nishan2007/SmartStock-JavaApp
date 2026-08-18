(function () {
  var completed = false;
  function status(message, failed) {
    var element = document.getElementById('startupStatus');
    if (!element) return;
    element.textContent = message;
    element.style.color = failed ? '#b91c1c' : '#64748b';
    element.style.fontWeight = failed ? '700' : '400';
  }
  window.smartstockMobileReady = function () {
    completed = true;
    status('Connected. Checking activation…', false);
  };
  window.addEventListener('error', function (event) {
    completed = true;
    status('The mobile page could not start: ' + (event.message || 'JavaScript failed to load.'), true);
  });
  window.setTimeout(function () {
    if (!completed) status('The mobile page script did not start. Reload this page or generate a new QR code.', true);
  }, 5000);
}());
