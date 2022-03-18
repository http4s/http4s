
function logError (req) {
  const status = req.status;
  const text = req.statusText;
  console.log(`[${status}]: ${text}`);
}

function populateMenu (data, localRootPrefix, currentPath, currentVersion) {
  const currentTarget = data.linkTargets.find(target => target.path === currentPath);
  const menuList = document.getElementById("version-list");
  data.versions.forEach(version => {
    const pathPrefix = localRootPrefix + version.pathSegment;
    const href = (currentTarget && currentTarget.versions.includes(version.pathSegment)) ?
        pathPrefix + currentPath : pathPrefix + version.fallbackLink;

    const link = document.createElement('a');
    if (version.label) {
      const span = document.createElement("span");
      span.innerText = version.label;
      span.classList.add(version.label.toLowerCase());
      span.classList.add("version-label");
      const wrapper = document.createElement("span");
      wrapper.classList.add("left-column");
      wrapper.appendChild(span);
      link.appendChild(wrapper);
    }
    link.appendChild(document.createTextNode(version.displayValue));
    link.setAttribute("href", href);
    document.body.appendChild(link);
    
    const listItem = document.createElement("li");
    listItem.classList.add("level1");
    if (version.pathSegment === currentVersion) listItem.classList.add("active");
    listItem.appendChild(link)

    menuList.appendChild(listItem);
  });
}

function loadVersions (localRootPrefix, currentPath, currentVersion) {
  const url = localRootPrefix + "laika/versionInfo.json";
  const req = new XMLHttpRequest();
  req.open("GET", url);
  req.responseType = "json";
  req.onload = () => {
    if (req.status === 200) {
      populateMenu(req.response, localRootPrefix, currentPath, currentVersion);
      initMenuToggle();
    }
    else logError(req)
  };
  req.onerror = () => {
    logError(req)
  };
  req.send();
}

function initMenuToggle () {
  document.addEventListener("click", (evt) => {
    const menuClicked = evt.target.closest("#version-menu");
    const buttonClicked = evt.target.closest("#version-menu-toggle");
    if (!menuClicked && !buttonClicked) {
      document.getElementById("version-menu").classList.remove("versions-open")
    }
  });
  document.getElementById("version-menu-toggle").onclick = () => {
    document.getElementById("version-menu").classList.toggle("versions-open");
  };
}

function initVersions (localRootPrefix, currentPath, currentVersion) {
  document.addEventListener('DOMContentLoaded', () => {
    loadVersions(localRootPrefix, currentPath, currentVersion);
  });
}
