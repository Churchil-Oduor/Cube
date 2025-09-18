import cv2 as cv
from april_tag_detector import AprilTagDetector



cap = cv.VideoCapture(0)
A_Detect = AprilTagDetector()

while True:
    isTrue, frame = cap.read()

    if not isTrue:
        break

    cv.imshow("frame", frame)
    detected_tags = A_Detect.pose_detected(frame)
    print(detected_tags)

    if cv.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv.destroyAllWindows()


