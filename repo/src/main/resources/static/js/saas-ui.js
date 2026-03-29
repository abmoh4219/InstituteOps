(function () {
  function byId(id) {
    return document.getElementById(id);
  }

  function showToast(message, tone) {
    var host = byId('toastHost');
    if (!host) {
      return;
    }
    var div = document.createElement('div');
    div.className = 'toast pointer-events-auto';
    var color = tone === 'error' ? 'text-rose-700' : 'text-emerald-700';
    div.innerHTML = '<p class="text-sm font-semibold ' + color + '">' + message + '</p>';
    host.appendChild(div);
    setTimeout(function () {
      div.remove();
    }, 3200);
  }

  function initSidebar() {
    var sidebar = byId('sidebar');
    var toggle = byId('sidebarToggle');
    if (!sidebar || !toggle) {
      return;
    }
    toggle.addEventListener('click', function () {
      sidebar.classList.toggle('-translate-x-full');
    });
  }

  function initQuickSearch() {
    var modal = byId('quickSearchModal');
    var input = byId('quickSearchInput');
    var btn = byId('quickSearchBtn');
    var results = byId('quickSearchResults');
    if (!modal || !input || !results) {
      return;
    }

    var links = Array.from(document.querySelectorAll('#sidebar a[href]')).map(function (a) {
      return { label: a.textContent.trim(), href: a.getAttribute('href') };
    });

    function render(filter) {
      var q = (filter || '').toLowerCase();
      var subset = links.filter(function (l) { return l.label.toLowerCase().indexOf(q) >= 0; }).slice(0, 8);
      results.innerHTML = subset.map(function (l) {
        return '<a class="block rounded-lg px-3 py-2 hover:bg-slate-100" href="' + l.href + '">' + l.label + '</a>';
      }).join('');
    }

    function open() {
      modal.classList.remove('hidden');
      input.value = '';
      render('');
      setTimeout(function () { input.focus(); }, 20);
    }

    function close() {
      modal.classList.add('hidden');
    }

    if (btn) {
      btn.addEventListener('click', open);
    }

    document.addEventListener('keydown', function (e) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        open();
      }
      if (e.key === 'Escape') {
        close();
      }
    });

    modal.addEventListener('click', function (e) {
      if (e.target === modal) {
        close();
      }
    });

    input.addEventListener('input', function () {
      render(input.value);
    });
  }

  function initFormFeedback() {
    Array.from(document.querySelectorAll('form')).forEach(function (form) {
      form.addEventListener('submit', function () {
        var button = form.querySelector('button[type="submit"]');
        if (!button) {
          return;
        }
        button.dataset.original = button.innerHTML;
        button.disabled = true;
        button.innerHTML = '<span class="spinner"></span> Saving...';
      });
    });

    Array.from(document.querySelectorAll('input,select,textarea')).forEach(function (el) {
      el.addEventListener('invalid', function () {
        el.classList.add('border-rose-400', 'ring-rose-100');
      });
      el.addEventListener('input', function () {
        el.classList.remove('border-rose-400', 'ring-rose-100');
      });
    });
  }

  function initCountdowns() {
    Array.from(document.querySelectorAll('[data-countdown-seconds]')).forEach(function (el) {
      var remaining = parseInt(el.getAttribute('data-countdown-seconds') || '0', 10);
      function tick() {
        if (remaining <= 0) {
          el.textContent = 'Expired';
          return;
        }
        var d = Math.floor(remaining / 86400);
        var h = Math.floor((remaining % 86400) / 3600);
        var m = Math.floor((remaining % 3600) / 60);
        var s = remaining % 60;
        el.textContent = d + 'd ' + h + 'h ' + m + 'm ' + s + 's';
        remaining -= 1;
        setTimeout(tick, 1000);
      }
      tick();
    });
  }

  function initPageToasts() {
    var params = new URLSearchParams(window.location.search);
    if (params.has('error')) {
      showToast('Action failed. Please check your input.', 'error');
    }
    if (params.has('success') || params.has('saved') || params.has('updated')) {
      showToast('Action completed successfully.', 'success');
    }
  }

  initSidebar();
  initQuickSearch();
  initFormFeedback();
  initCountdowns();
  initPageToasts();
})();
