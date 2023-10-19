
function logError (req) {
  const status = req.status;
  const text = req.statusText;
  console.log(`[${status}]: ${text}`);
}

function populateMenu (data, currentPath, currentVersion, relativeRoot, absoluteRoot) {
  
  const currentTarget = data.linkTargets.find(target => target.path === currentPath);
  let canonicalLink;
  
  const listItems = data.versions.map(version => {
    const pathPrefix = relativeRoot + version.pathSegment;
    const hasMatchingLink = currentTarget && currentTarget.versions.includes(version.pathSegment);
    const href = (hasMatchingLink) ? pathPrefix + currentPath : pathPrefix + version.fallbackLink;
    if (version.canonical && hasMatchingLink) {
      const versionedPath = version.pathSegment + currentPath
      canonicalLink = (absoluteRoot != null) ? absoluteRoot + versionedPath : relativeRoot + versionedPath;
    }

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
    
    const listItem = document.createElement("li");
    listItem.classList.add("level1");
    if (version.pathSegment === currentVersion) listItem.classList.add("active");
    listItem.appendChild(link);

    return listItem;
  });

  const versionMenus = document.querySelectorAll(".version-menu");
  versionMenus.forEach((container, index) => {
    const list = container.querySelector(".nav-list")
    if (list) {
      const children = index === 0 ? listItems : listItems.map((item) => item.cloneNode(true));
      list.prepend(...children);
    }
  });
  
  return canonicalLink;
}

function loadVersions (currentPath, currentVersion, relativeRoot, absoluteRoot) {
  const url = relativeRoot + "laika/versionInfo.json";
  const req = new XMLHttpRequest();
  req.open("GET", url);
  req.responseType = "json";
  req.onload = () => {
    if (req.status === 200) {
      const canonicalLink = populateMenu(req.response, currentPath, currentVersion, relativeRoot, absoluteRoot);
      if (canonicalLink) insertCanonicalLink(canonicalLink);
    }
    else logError(req)
  };
  req.onerror = () => {
    logError(req)
  };
  req.send();
}

function insertCanonicalLink (linkHref) {
  if (!document.querySelector("link[rel='canonical']")) {
    const head = document.head;
    const link = document.createElement("link");
    link.setAttribute("rel", "canonical");
    link.setAttribute("href", linkHref);
    head.appendChild(link);
  }
}

function initVersions (relativeRoot, currentPath, currentVersion, absoluteRoot) {
  document.addEventListener('DOMContentLoaded', () => {
    if (document.querySelectorAll(".version-menu").length > 0)
      loadVersions(currentPath, currentVersion, relativeRoot, absoluteRoot);
  });
}
