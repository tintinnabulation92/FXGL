/*
 * The MIT License (MIT)
 *
 * FXGL - JavaFX Game Library
 *
 * Copyright (c) 2015 AlmasB (almaslvl@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.almasb.fxgl.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import com.almasb.fxgl.GameApplication;
import com.almasb.fxgl.entity.Control;
import com.almasb.fxgl.entity.Entity;

/**
 * Allows to call a few pre-configured special effects involving
 * particles
 *
 * @author Almas Baimagambetov (AlmasB) (almaslvl@gmail.com)
 * @version 1.0
 *
 */
public final class ParticleManager {

    private GameApplication app;

    private Random random = new Random();

    public ParticleManager(GameApplication app) {
        this.app = app;
    }

    public void spawnExplosion(Point2D point, Color color, int radius, int numParticles) {
        List<Entity> particles = new ArrayList<>();

        for (int i = 0; i < numParticles; i++) {
            Rectangle rect = new Rectangle(10, 1);
            rect.setFill(color);

            Entity particle = Entity.noType()
                    .setGraphics(rect)
                    .setPosition(point)
                    .setProperty("v", getRandomVelocity(radius));

            particle.addControl(new Control() {
                @Override
                public void onUpdate(Entity entity, long now) {
                    Point2D velocity = entity.getProperty("v");

                    entity.translate(velocity.multiply(0.016 * 3f));
                    velocity = velocity.multiply(1 - 3f * 0.016);
                    if (Math.abs(velocity.getX()) + Math.abs(velocity.getY()) < 0.001f) {
                        velocity = new Point2D(0, 0);
                    }

                    if (velocity.getX() != 0 && velocity.getY() != 0) {
                        double angle = Math.toDegrees(Math.atan(velocity.getY() / velocity.getX()));
                        angle = velocity.getX() > 0 ? angle : 180 + angle;

                        entity.getTransforms().setAll(new Rotate(angle));
                    }

                    entity.setProperty("v", velocity);
                }
            });

            FadeTransition ft = new FadeTransition(Duration.seconds(2), particle);
            ft.setToValue(0);
            ft.play();

            particles.add(particle);
            app.addEntities(particle);
        }

        app.runOnceAfter(() -> particles.forEach(app::removeEntity), 2 * GameApplication.SECOND);
    }

    public void spawnImplosion(Point2D point, Color color, int radius, int numParticles) {
        List<Entity> particles = new ArrayList<>();

        for (int i = 0; i < numParticles; i++) {
            Rectangle rect = new Rectangle(10, 1);
            rect.setFill(color);

            Entity particle = Entity.noType()
                    .setGraphics(rect)
                    .setPosition(point.add(getRandomVelocity(radius)));

            particle.addControl(new Control() {
                @Override
                public void onUpdate(Entity entity, long now) {
                    Point2D velocity = entity.getPosition().subtract(point);

                    if (velocity.getX() != 0 && velocity.getY() != 0) {
                        double angle = Math.toDegrees(Math.atan(velocity.getY() / velocity.getX()));
                        angle = velocity.getX() > 0 ? angle : 180 + angle;

                        entity.getTransforms().setAll(new Rotate(angle));
                    }
                }
            });

            TranslateTransition tt = new TranslateTransition(Duration.seconds(1), particle);
            tt.setToX(point.getX());
            tt.setToY(point.getY());
            tt.setInterpolator(Interpolator.LINEAR);
            tt.play();

            particles.add(particle);
            app.addEntities(particle);
        }

        app.runOnceAfter(() -> particles.forEach(app::removeEntity), 1 * GameApplication.SECOND);
    }

    private Point2D getRandomVelocity(float max) {
        Point2D velocity = new Point2D(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalize();

        float rand = random.nextFloat() * 5 + 1;
        float particleSpeed = max * (1f - 0.6f / rand);
        return velocity.multiply(particleSpeed);
    }
}