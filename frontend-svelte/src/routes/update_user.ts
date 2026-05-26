import {currentUser} from "$lib/user";

export default function () {
    fetch("https://authentikt-lib.werkbank.space/api/user/me", {
        credentials: "include",
    }).then(response => {
        if (response.status === 401) currentUser.set("anonymous")
        else response.json().then(user => currentUser.set(user))
    })
}