import {currentUser} from "$lib/user.ts";

export default function () {
    fetch("https://authentikt-lib.wb.local/api/user/me", {
        credentials: "include",
    }).then(response => {
        if (response.status === 401) currentUser.set("anonymous")
        else response.json().then(user => currentUser.set(user))
    })
}