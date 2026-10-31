const ALLOWED_IMAGE_HOSTS = new Set(["cards.scryfall.io", "c1.scryfall.com"]);

export async function GET(request: Request) {
  const source = new URL(request.url).searchParams.get("url");
  if (!source) return new Response("Missing image URL", { status: 400 });

  try {
    const url = new URL(source);
    if (url.protocol !== "https:" || !ALLOWED_IMAGE_HOSTS.has(url.hostname)) {
      return new Response("Image source is not allowed", { status: 400 });
    }

    const image = await fetch(url, {
      headers: {
        Accept: "image/avif,image/webp,image/jpeg,image/png,image/*",
        "User-Agent": "TheFourthSeat/1.0",
      },
    });
    if (!image.ok || !image.body) return new Response("Card image unavailable", { status: 502 });

    return new Response(image.body, {
      headers: {
        "Content-Type": image.headers.get("Content-Type") ?? "image/jpeg",
        "Cache-Control": "public, max-age=86400, stale-while-revalidate=604800",
        "X-Content-Type-Options": "nosniff",
      },
    });
  } catch {
    return new Response("Invalid image URL", { status: 400 });
  }
}
