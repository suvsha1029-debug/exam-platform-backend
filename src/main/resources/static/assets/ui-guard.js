(function () {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*_-+=?';

  function randomGuardText(length = 96) {
    const bytes = new Uint8Array(length);
    if (window.crypto && window.crypto.getRandomValues) {
      window.crypto.getRandomValues(bytes);
    } else {
      for (let i = 0; i < bytes.length; i += 1) bytes[i] = Math.floor(Math.random() * 256);
    }
    return Array.from(bytes, (byte) => alphabet[byte % alphabet.length]).join('');
  }

  function installStyle() {
    const style = document.createElement('style');
    style.textContent = `
      html, body, body * {
        -webkit-user-select: none !important;
        -moz-user-select: none !important;
        -ms-user-select: none !important;
        user-select: none !important;
        -webkit-touch-callout: none !important;
      }
      input, textarea, select, button {
        -webkit-user-select: none !important;
        user-select: none !important;
      }
    `;
    document.head.appendChild(style);
  }

  function blockEvent(event) {
    event.preventDefault();
    event.stopPropagation();
    return false;
  }

  function handleClipboard(event) {
    event.preventDefault();
    event.stopPropagation();
    const value = randomGuardText();
    if (event.clipboardData) {
      event.clipboardData.setData('text/plain', value);
      event.clipboardData.setData('text/html', value);
      return false;
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(value).catch(() => {});
    }
    return false;
  }

  function guardedShortcut(event) {
    const key = String(event.key || '').toLowerCase();
    return /^F([1-9]|1[0-2])$/.test(event.key || '')
      || event.key === 'PrintScreen'
      || event.key === 'ContextMenu'
      || event.key === 'Meta'
      || event.key === 'OS'
      || (event.ctrlKey && key === 'u')
      || (event.ctrlKey && key === 's')
      || (event.ctrlKey && key === 'p')
      || (event.ctrlKey && key === 'c')
      || (event.ctrlKey && key === 'x')
      || (event.ctrlKey && key === 'a')
      || (event.ctrlKey && key === 'r')
      || (event.ctrlKey && ['+', '-', '='].includes(key))
      || (event.altKey && ['tab', 'escape', 'f4', 'space'].includes(key))
      || (event.ctrlKey && event.shiftKey && ['i', 'j', 'c', 'k'].includes(key))
      || (event.metaKey && ['c', 'x', 's', 'p'].includes(key))
      || event.metaKey
      || (event.metaKey && event.altKey && ['i', 'j', 'c'].includes(key));
  }

  installStyle();
  document.addEventListener('contextmenu', blockEvent, true);
  document.addEventListener('selectstart', blockEvent, true);
  document.addEventListener('dragstart', blockEvent, true);
  document.addEventListener('copy', handleClipboard, true);
  document.addEventListener('cut', handleClipboard, true);
  document.addEventListener('keydown', (event) => {
    if (guardedShortcut(event)) {
      if ((event.ctrlKey || event.metaKey) && ['c', 'x'].includes(String(event.key || '').toLowerCase())) {
        handleClipboard(event);
      } else {
        blockEvent(event);
      }
    }
  }, true);
})();
