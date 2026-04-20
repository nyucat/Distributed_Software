const authShell = document.getElementById("auth-shell");
const loginTab = document.getElementById("login-tab");
const registerTab = document.getElementById("register-tab");
const loginForm = document.getElementById("login-form");
const registerForm = document.getElementById("register-form");
const toastEl = document.getElementById("toast");

const serverPortEl = document.getElementById("server-port");
const lastActionEl = document.getElementById("last-action");
const resultMessageEl = document.getElementById("result-message");
const resultUserIdEl = document.getElementById("result-user-id");
const resultUsernameEl = document.getElementById("result-username");
const resultPhoneEl = document.getElementById("result-phone");
const resultTokenEl = document.getElementById("result-token");

function setMode(mode) {
    const isRegister = mode === "register";

    authShell.classList.toggle("is-register", isRegister);
    loginTab.classList.toggle("active", !isRegister);
    registerTab.classList.toggle("active", isRegister);
    loginForm.classList.toggle("active", !isRegister);
    registerForm.classList.toggle("active", isRegister);
}

function showToast(message, type) {
    toastEl.textContent = message;
    toastEl.classList.remove("ok", "error", "show");
    toastEl.classList.add(type === "ok" ? "ok" : "error");
    toastEl.classList.add("show");
    setTimeout(() => {
        toastEl.classList.remove("show");
    }, 2200);
}

function setLastAction(text) {
    lastActionEl.textContent = text;
}

function updateServerPortFromResponse(response) {
    const servedBy = response.headers.get("X-Served-By");
    if (servedBy) {
        serverPortEl.textContent = servedBy;
    }
}

function setResultCard(info) {
    resultMessageEl.textContent = info.message || "操作完成";
    resultUserIdEl.textContent = info.userId || "-";
    resultUsernameEl.textContent = info.username || "-";
    resultPhoneEl.textContent = info.phone || "-";
    resultTokenEl.textContent = info.token || "-";
}

function readValue(id) {
    return document.getElementById(id).value.trim();
}

function tokenPreview(token) {
    if (!token) {
        return "-";
    }
    if (token.length <= 16) {
        return token;
    }
    return `${token.slice(0, 8)}...${token.slice(-8)}`;
}

async function postJson(url, payload) {
    const response = await fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
    });

    updateServerPortFromResponse(response);

    let data;
    try {
        data = await response.json();
    } catch (e) {
        throw new Error("服务返回了无效响应");
    }

    return data;
}

loginTab.addEventListener("click", () => setMode("login"));
registerTab.addEventListener("click", () => setMode("register"));

loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const username = readValue("login-username");
    const password = readValue("login-password");

    if (!username || !password) {
        showToast("请填写用户名和密码", "error");
        return;
    }

    const submitBtn = loginForm.querySelector("button[type='submit']");
    submitBtn.disabled = true;
    submitBtn.textContent = "登录中...";
    setLastAction("Submitting Login");

    try {
        const result = await postJson("/api/user/login", { username, password });

        if (result.code === 200 && result.data) {
            const user = result.data.userInfo || {};
            const token = result.data.token || "";

            localStorage.setItem("seckill_token", token);
            localStorage.setItem("seckill_user", JSON.stringify(user));

            setResultCard({
                message: "登录成功，已写入本地凭证。",
                userId: user.userId,
                username: user.username,
                phone: user.phone,
                token: tokenPreview(token)
            });

            showToast("登录成功", "ok");
            setLastAction("Login Success");
        } else {
            throw new Error(result.message || "登录失败");
        }
    } catch (error) {
        showToast(error.message, "error");
        setResultCard({ message: `登录失败: ${error.message}` });
        setLastAction("Login Failed");
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = "立即登录";
    }
});

registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    const username = readValue("register-username");
    const phone = readValue("register-phone");
    const password = readValue("register-password");

    if (username.length < 3 || username.length > 20) {
        showToast("用户名长度需在 3-20 位", "error");
        return;
    }

    if (!/^1\d{10}$/.test(phone)) {
        showToast("手机号格式不正确", "error");
        return;
    }

    if (password.length < 6) {
        showToast("密码至少 6 位", "error");
        return;
    }

    const submitBtn = registerForm.querySelector("button[type='submit']");
    submitBtn.disabled = true;
    submitBtn.textContent = "注册中...";
    setLastAction("Submitting Register");

    try {
        const result = await postJson("/api/user/register", { username, password, phone });

        if (result.code === 200) {
            showToast("注册成功，请登录", "ok");
            setResultCard({ message: "注册成功，已自动切换到登录页。" });
            setLastAction("Register Success");

            document.getElementById("login-username").value = username;
            document.getElementById("login-password").value = password;
            setMode("login");
        } else {
            throw new Error(result.message || "注册失败");
        }
    } catch (error) {
        showToast(error.message, "error");
        setResultCard({ message: `注册失败: ${error.message}` });
        setLastAction("Register Failed");
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = "完成注册";
    }
});

setMode("login");
