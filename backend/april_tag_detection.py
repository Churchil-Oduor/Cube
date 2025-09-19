import cv2 as cv
import numpy as np
from pupil_apriltags import Detector


detector = Detector(
        families = "tag36h11",
        nthreads=1,
        quad_decimate=1.0,
        quad_sigma=0.0,
        refine_edges=1,
        decode_sharpening=0.25,
        debug=0
        )

fx, fy = 600, 600 # focal lengths in pixels
cx, cy = 320, 240 # principal point (image center)
camera_params = [fx, fy, cx, cy]

tag_size = 0.165
cap = cv.VideoCapture(0)


while True:
    isTrue, frame = cap.read()

    if not isTrue:
        break

u   gray = cv.cvtColor(frame, cv.COLOR_BGR2GRAY)
    tags = detector.detect(
            gray,
            estimate_tag_pose = True,
            camera_params = camera_params,
            tag_size=tag_size
            )

    # drawing over the detected params
    for tag in tags:
        corners = tag.corners.astype(int)
        for i in range(4):
            cv.line(frame, tuple(corners[i]), tuple(corners[(i + 1) % 4] ), (0, 255, 0), 2)

        #pose rotation
        pose_R = tag.pose_R
        pose_t = tag.pose_t

        axis_length = tag_size * 0.5
        axis_points = np.float32([
            [0, 0, 0], #origin
            [axis_length, 0, 0], # X-axis
            [0, axis_length * -1 , 0], # Y-axis
            [0, 0, axis_length, ] # Z-axis
            ]).reshape(-1, 3)

        camera_matrix = np.array([
            [fx, 0, cx],
            [0, fy, cy],
            [0, 0, 1]
            ], dtype=np.float32) 

        dist_coeffs = np.zeros((4, 1))

        #projecting 3D axis points onto 2D image plane

        axis_2d, _ = cv.projectPoints(
            axis_points,
            pose_R,
            pose_t,
            camera_matrix,
            dist_coeffs

            )

        axis_2d = axis_2d.reshape(-1, 2).astype(int)

        origin = tuple(axis_2d[0])

        cv.line(frame, origin, tuple(axis_2d[1]), (0, 0, 255), 2)  # X-axis
        cv.line(frame, origin, tuple(axis_2d[2]), (0, 255, 0), 2)  # Y-axis
        cv.line(frame, origin, tuple(axis_2d[3]), (255, 0, 0), 2)  # Z-axis

        print("{}".format(axis_2d[1]))
    cv.imshow("Frame", frame)
    if cv.waitKey(1) & 0xFF == ord('q'):
        break

cap.release()
cv.destroyAllWindows()
