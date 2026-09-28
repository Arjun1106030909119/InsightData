// ✏️ Change this to your Render URL after deployment:
// e.g. "https://insightdata-xxxx.onrender.com"
const API_BASE_URL = "http://localhost:8080";

document.addEventListener('DOMContentLoaded', () => {
    // Restore saved notes
    chrome.storage.local.get(['researchNotes'], function(result) {
        if (result.researchNotes) {
            document.getElementById('notes').value = result.researchNotes;
        }
    });

    document.getElementById('summarizeBtn').addEventListener('click', () => processText('summarize'));
    document.getElementById('suggestBtn').addEventListener('click', () => processText('suggest'));
    document.getElementById('saveNotesBtn').addEventListener('click', saveNotes);
});


async function processText(operation) {
    const btn = document.getElementById(
        operation === 'summarize' ? 'summarizeBtn' : 'suggestBtn'
    );

    try {
        // Get selected text from the active tab
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

        // Guard: can't inject scripts into chrome:// or edge:// pages
        if (!tab || !tab.url || tab.url.startsWith('chrome://') || tab.url.startsWith('edge://')) {
            showResult('⚠️ Cannot read text from this page. Try a regular website.', true);
            return;
        }

        const [{ result }] = await chrome.scripting.executeScript({
            target: { tabId: tab.id },
            function: () => window.getSelection().toString()
        });

        if (!result || result.trim() === '') {
            showResult('⚠️ Please select some text on the page first.', true);
            return;
        }

        // Show loading state
        btn.disabled = true;
        btn.textContent = '⏳ Processing...';
        showResult('Loading...', false);

        const response = await fetch(`${API_BASE_URL}/api/research/process`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ content: result, operation: operation })
        });

        if (!response.ok) {
            const errText = await response.text();
            throw new Error(`Server error ${response.status}: ${errText}`);
        }

        const text = await response.text();
        showResult(text.replace(/\n/g, '<br>'), false);

    } catch (error) {
        showResult('❌ Error: ' + error.message, true);
    } finally {
        // Restore button
        btn.disabled = false;
        btn.textContent = operation === 'summarize' ? '📝 Summarize' : '💡 Suggest Topics';
    }
}


async function saveNotes() {
    const notes = document.getElementById('notes').value;
    chrome.storage.local.set({ 'researchNotes': notes }, function() {
        const btn = document.getElementById('saveNotesBtn');
        btn.textContent = '✅ Saved!';
        setTimeout(() => { btn.textContent = '💾 Save Notes'; }, 1500);
    });
}


function showResult(content, isError) {
    const resultsDiv = document.getElementById('results');
    resultsDiv.innerHTML = `
        <div class="result-item ${isError ? 'result-error' : ''}">
            <div class="result-content">${content}</div>
        </div>`;
}