# Third-party components

This unofficial, experimental client is not affiliated with Wizards of the Coast. Magic: The Gathering names and card artwork belong to their respective owners. Artwork is requested from Scryfall when online; a card-art database is not redistributed.

XMage and the Nexus bridge are redistributed with their original license texts in `engine/XMage-LICENSE.txt` and `engine/Nexus-LICENSE.txt`. Corresponding source, exact upstream commits, patches and build instructions accompany the release source bundle. The release also includes the exact pinned upstream source archives.

Eclipse Temurin / OpenJDK 17: retain the bundled `jre/legal` directory and its license notices (GPLv2 with Classpath Exception and other included notices). Source: https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.20%2B8
Node.js 24.19.0: retain `node/LICENSE`. Source: https://nodejs.org/dist/v24.19.0/node-v24.19.0.tar.gz
ws 8.18.3: MIT, license retained in node_modules/ws/LICENSE.
React and React DOM 19.2.6: MIT; license texts included in licenses/.
Tailwind CSS 4.2.1: MIT; license text included in licenses/.

This package ships only client files necessary for local play from The Fourth Seat. The private website admin, database, and production credentials are not included. The UI changes and desktop integration source are in the matching release source bundle.
