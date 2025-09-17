import cv2
import numpy as np
from fastapi import FastAPI, WebSocket
import uvicorn

app = FastAPI()

@app.websocket("/stream")
async def websocket_stream(websocket: WebSocket):
    await websocket.accept()
    cv2.namedWindow("Phone Camera Stream", cv2.WINDOW_NORMAL)
    try:
        while True:
            data = await websocket.receive_bytes()
            # Decode JPEG bytes to image
            img_array = np.frombuffer(data, np.uint8)
            img = cv2.imdecode(img_array, cv2.IMREAD_COLOR)
            if img is not None:
                cv2.imshow("Phone Camera Stream", img)
                if cv2.waitKey(1) & 0xFF == ord('q'):  # Press 'q' to quit
                    break
    except Exception as e:
        print(f"Error: {e}")
    finally:
        await websocket.close()
        cv2.destroyAllWindows()

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
