(function(){
  function track(name,data){
    try{if(typeof window.va==='function') window.va('event',{name:name,data:data||{}});}catch(e){}
  }
  document.querySelectorAll('[data-track="download"]').forEach(function(el){
    el.addEventListener('click',function(){track('APK Download Click',{version:'4.3.0',location:el.getAttribute('data-location')||location.pathname});});
  });
  document.querySelectorAll('[data-track="donation"]').forEach(function(el){
    el.addEventListener('click',function(){track('Donation Click',{provider:'Ko-fi',location:location.pathname});});
  });
})();