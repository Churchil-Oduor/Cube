import cv2
from april_tag_detector import AprilTagDetector
import numpy as np
from fastapi import FastAPI, WebSocket
import uvicorn
import json


app = FastAPI()
Pose_detector = AprilTagDetector()

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
                pose = Pose_detector.pose_detected(img)

                if pose:
                    pose_serializable = {k: v.tolist() for k, v in pose.items()}
                    await websocket.send_text(json.dumps(pose_serializable))
                    print(pose_serializable)

                if cv2.waitKey(1) & 0xFF == ord('q'):  # Press 'q' to quit
                    break
    except Exception as e:
        print(f"Error: {e}")
    finally:
        await websocket.close()
        cv2.destroyAllWindows()

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
