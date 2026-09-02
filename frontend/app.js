const form = document.getElementById('calculator-form');
const result = document.getElementById('result');
const history = document.getElementById('history');
const refreshHistory = document.getElementById('refresh-history');

// /config.js se genera al iniciar el Frontend con la URL alcanzable del Backend.
const backendUrl = window.APP_CONFIG?.backendUrl;

if (!backendUrl) {
    throw new Error('No se configuró la URL del Backend.');
}

const labels = {
    sum: '+',
    subtract: '-',
    multiply: '×',
    divide: '/'
};

form.addEventListener('submit', async event => {
    event.preventDefault();

    const a = document.getElementById('a').value;
    const b = document.getElementById('b').value;
    const operation = document.getElementById('operation').value;

    result.textContent = 'Procesando...';

    try {
        const response = await fetch(`${backendUrl}/api/${operation}`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({a: Number(a), b: Number(b)})
        });
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'No fue posible realizar el cálculo');
        }

        result.textContent = `Resultado: ${data.result}`;
        await loadHistory();
    } catch (error) {
        result.textContent = `Error: ${error.message}`;
    }
});

refreshHistory.addEventListener('click', loadHistory);

async function loadHistory() {
    history.innerHTML = '<li>Cargando...</li>';

    try {
        const response = await fetch(`${backendUrl}/api/history`);
        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.error || 'No fue posible consultar el historial');
        }

        if (data.length === 0) {
            history.innerHTML = '<li>No hay operaciones registradas.</li>';
            return;
        }

        history.innerHTML = '';
        data.forEach(item => {
            const li = document.createElement('li');
            li.textContent = `${item.a} ${labels[item.operation] || item.operation} ${item.b} = ${item.result}`;
            history.appendChild(li);
        });
    } catch (error) {
        history.innerHTML = `<li>Error: ${error.message}</li>`;
    }
}

loadHistory();
