(function () {
  const form = document.getElementById("extractForm");
  const fileInput = document.getElementById("pdfFile");
  const formatSelect = document.getElementById("format");
  const profileSelect = document.getElementById("profile");
  const subjectInput = document.getElementById("subject");
  const dpiInput = document.getElementById("dpi");
  const statusNode = document.getElementById("statusText");
  const profileChip = document.getElementById("profileChip");
  const countChip = document.getElementById("countChip");
  const formatChip = document.getElementById("formatChip");
  const outputPreview = document.getElementById("outputPreview");
  const downloadBtn = document.getElementById("downloadBtn");

  let lastPayload = null;

  function setStatus(text) {
    statusNode.textContent = text;
  }

  function updateSummary(result) {
    profileChip.textContent = `Profile: ${result.profileId || "auto"}`;
    countChip.textContent = `Questions: ${result.questionCount || 0}`;
    formatChip.textContent = `Format: ${result.format || formatSelect.value}`;
  }

  function downloadJson() {
    if (!lastPayload) return;
    const blob = new Blob([JSON.stringify(lastPayload, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `pdf-extract-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function extractPdf(event) {
    event.preventDefault();
    if (!fileInput.files || !fileInput.files[0]) {
      setStatus("Choose a PDF first.");
      return;
    }

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);
    formData.append("format", formatSelect.value);
    formData.append("profile", profileSelect.value);
    formData.append("subject", subjectInput.value || "GEN");
    formData.append("dpi", dpiInput.value || "200");

    setStatus("Extracting PDF...");
    downloadBtn.disabled = true;

    try {
      const response = await fetch("/api/pdf-extractor/extract", {
        method: "POST",
        body: formData,
      });
      const result = await response.json();
      if (!response.ok) {
        throw new Error(result.detail || result.title || "Extraction failed");
      }
      lastPayload = result.payload;
      updateSummary(result);
      outputPreview.textContent = JSON.stringify(result.payload, null, 2);
      downloadBtn.disabled = false;
      setStatus("Extraction complete.");
    } catch (error) {
      lastPayload = null;
      outputPreview.textContent = "";
      downloadBtn.disabled = true;
      setStatus(`Extraction failed: ${error.message || error}`);
    }
  }

  form.addEventListener("submit", extractPdf);
  downloadBtn.addEventListener("click", downloadJson);
})();
