let id = "";
let ws;

function changeInput(e) {
    id = e.target.value;
    document.querySelectorAll(".id-button").forEach(el => {
        el.disabled = id.length <= 0;
    });
}

function listenButton(e) {
    const info = document.querySelector("#listen-info");

    if (!ws) {
        ws = new WebSocket(`api/${id}/listen`);
        ws.onopen = (event) => {
            ws.onmessage = (event) => {
                console.log(event.data);
                if (event.data === "c") {
                    let audio = new Audio('sound.ogg');
                    audio.play();
                }
            };

            ws.onclose = () => {
                ws = null;
                e.target.innerText = "Listen";
                info.innerText = "";
            }
            ws.onerror = () => {
                ws = null;
                e.target.innerText = "Listen";
                info.innerText = "";
            }
        };
        e.target.innerText = "Stop Listening";
        info.innerText = `Listening on '${id}'`;
    } else {
        ws.close();
        ws = null;
        e.target.innerText = "Listen";                
        info.innerText = "";
    }
}

async function clickButton(e) {
    await fetch(`api/${id}/click`);
    e.target.innerText = "Clicked!";
    setTimeout(() => {
        e.target.innerText = "Click";
    }, 1000);
}

document.querySelector("#id-input").value = "";
document.querySelectorAll(".id-button").forEach(el => {
    el.disabled = true;
});
