import {currentUser} from "$lib/user";

export default function () {
    fetch("http://localhost:8080/api/user/me", {
        credentials: "include",
    }).then(response => {
        if (response.status === 401) currentUser.set("anonymous")
        else response.json().then(user => currentUser.set(user))
    })
}